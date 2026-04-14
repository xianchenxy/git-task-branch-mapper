package com.branchmapping.plugin.ui

import com.branchmapping.plugin.data.BranchMappingFileRepository
import com.branchmapping.plugin.model.BranchMappingStatus
import com.branchmapping.plugin.model.BranchMappingViewState
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import git4idea.repo.GitRepositoryManager
import java.awt.BorderLayout
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class BranchMappingToolWindowContent(
    private val project: Project,
) : Disposable {

    private val repository = BranchMappingFileRepository()
    private val stateJson = Json { encodeDefaults = true }
    private val queryJson = Json { ignoreUnknownKeys = true }

    private var browser: JBCefBrowser? = null
    private var jsQuery: JBCefJSQuery? = null

    val component: JComponent = createComponent()

    private fun createComponent(): JComponent {
        if (!JBCefApp.isSupported()) {
            return JPanel(BorderLayout()).apply {
                add(JLabel("当前 IDE 环境不支持 JCEF"), BorderLayout.CENTER)
            }
        }

        val jcefBrowser = JBCefBrowser("about:blank")
        browser = jcefBrowser

        val frontendQuery = JBCefJSQuery.create(jcefBrowser)
        jsQuery = frontendQuery
        frontendQuery.addHandler { payload ->
            handleFrontendCommand(payload)
            null
        }

        jcefBrowser.jbCefClient.addLoadHandler(
            object : CefLoadHandlerAdapter() {
                override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                    if (frame?.isMain != true) {
                        return
                    }

                    requestStateReload()
                }
            },
            jcefBrowser.cefBrowser,
        )

        jcefBrowser.loadHTML(buildPageHtml(frontendQuery.inject("payload")))

        return JPanel(BorderLayout()).apply {
            add(jcefBrowser.component, BorderLayout.CENTER)
        }
    }

    private fun handleFrontendCommand(payload: String) {
        if (payload == RELOAD_COMMAND) {
            requestStateReload()
            return
        }

        val commandJson = runCatching {
            queryJson.parseToJsonElement(payload).jsonObject
        }.getOrNull() ?: return

        val command = commandJson["command"]?.jsonPrimitive?.contentOrNull
        if (command == ADD_MAPPING_COMMAND) {
            val addCommand = parseAddMappingCommand(commandJson) ?: return
            if (addCommand.branchName.isBlank() || addCommand.requirementName.isBlank()) {
                return
            }

            requestAddMapping(addCommand)
            return
        }

        if (command == DELETE_MAPPINGS_COMMAND) {
            val branchNames = commandJson["branchNames"]?.jsonArray
                ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
                ?.filter { it.isNotEmpty() }
                ?.distinct()
                .orEmpty()

            if (branchNames.isEmpty()) {
                return
            }

            requestDeleteMappings(branchNames)
        }
    }

    private fun parseAddMappingCommand(commandJson: JsonObject): AddMappingCommand? {
        return AddMappingCommand(
            branchName = commandJson["branchName"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
            requirementName = commandJson["requirementName"]?.jsonPrimitive?.contentOrNull.orEmpty().trim(),
        )
    }

    private fun requestAddMapping(command: AddMappingCommand) {
        ApplicationManager.getApplication().executeOnPooledThread {
            when (repository.saveMapping(project, command.branchName, command.requirementName)) {
                is BranchMappingFileRepository.SaveResult.Success -> {
                    requestStateReload()
                }

                is BranchMappingFileRepository.SaveResult.Failure -> {
                    requestStateReload()
                }
            }
        }
    }

    private fun requestDeleteMappings(branchNames: List<String>) {
        ApplicationManager.getApplication().executeOnPooledThread {
            when (repository.deleteMappings(project, branchNames)) {
                is BranchMappingFileRepository.SaveResult.Success -> {
                    requestStateReload()
                }

                is BranchMappingFileRepository.SaveResult.Failure -> {
                    requestStateReload()
                }
            }
        }
    }

    private fun requestStateReload() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val viewState = toViewState(
                result = repository.load(project),
                defaultBranchName = resolveCurrentBranchName(),
            )
            ApplicationManager.getApplication().invokeLater {
                val jcefBrowser = browser ?: return@invokeLater
                val payload = stateJson.encodeToString(viewState)
                val script = "window.BranchMappingApp && window.BranchMappingApp.setState($payload);"
                jcefBrowser.cefBrowser.executeJavaScript(script, jcefBrowser.cefBrowser.url, 0)
            }
        }
    }

    private fun toViewState(
        result: BranchMappingFileRepository.LoadResult,
        defaultBranchName: String,
    ): BranchMappingViewState {
        return when (result) {
            is BranchMappingFileRepository.LoadResult.Success -> BranchMappingViewState(
                status = BranchMappingStatus.READY,
                items = result.items,
                defaultBranchName = defaultBranchName,
            )

            BranchMappingFileRepository.LoadResult.Empty -> BranchMappingViewState(
                status = BranchMappingStatus.EMPTY,
                items = emptyList(),
                message = "暂无映射数据",
                defaultBranchName = defaultBranchName,
            )

            is BranchMappingFileRepository.LoadResult.ReadFailure -> BranchMappingViewState(
                status = BranchMappingStatus.ERROR,
                items = emptyList(),
                message = result.message,
                defaultBranchName = defaultBranchName,
            )
        }
    }

    private fun resolveCurrentBranchName(): String {
        val branchFromIde = resolveBranchFromIde()
        if (branchFromIde.isNotBlank()) {
            return branchFromIde
        }
        val projectBasePath = project.basePath ?: return ""
        return try {
            val process = ProcessBuilder(
                "git",
                "-C",
                projectBasePath,
                "rev-parse",
                "--abbrev-ref",
                "HEAD",
            )
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return ""
            }

            if (process.exitValue() != 0) {
                return ""
            }

            val branchName = process.inputStream.bufferedReader().use { reader ->
                reader.readText().trim()
            }
            if (branchName.isBlank() || branchName == "HEAD") {
                ""
            } else {
                branchName
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun resolveBranchFromIde(): String {
        return runCatching {
            val manager = GitRepositoryManager.getInstance(project)
            val projectRoot = project.basePath?.let { rootPath ->
                LocalFileSystem.getInstance().findFileByPath(rootPath)
            }
            val branchName = projectRoot?.let { root ->
                manager.getRepositoryForRootQuick(root)?.currentBranchName
            } ?: manager.repositories.firstOrNull()?.currentBranchName
            branchName?.trim().orEmpty()
        }.getOrDefault("")
    }

    override fun dispose() {
        jsQuery?.dispose()
        browser?.dispose()
        jsQuery = null
        browser = null
    }

    private fun buildPageHtml(queryInjection: String): String {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8" />
              <title>Git Task Branch Mapper</title>
              <style>
                :root {
                  color-scheme: light;
                  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
                }

                * {
                  box-sizing: border-box;
                }

                body {
                  margin: 0;
                  padding: 16px;
                  background: #f5f6f8;
                  color: #121417;
                }

                .toolbar {
                  display: flex;
                  gap: 8px;
                  margin-bottom: 8px;
                }

                .search-input {
                  flex: 1;
                  border: 1px solid #c8ccd4;
                  border-radius: 8px;
                  padding: 8px 10px;
                  font-size: 14px;
                }

                .reload-button {
                  border: 1px solid #2c6bed;
                  background: #2c6bed;
                  color: #ffffff;
                  border-radius: 8px;
                  padding: 8px 12px;
                  font-size: 13px;
                  cursor: pointer;
                }

                .add-button {
                  border: 1px solid #16a34a;
                  background: #16a34a;
                  color: #ffffff;
                  border-radius: 8px;
                  padding: 8px 12px;
                  font-size: 13px;
                  cursor: pointer;
                }

                .manage-button {
                  border: 1px solid #c8ccd4;
                  background: #ffffff;
                  color: #1f2937;
                  border-radius: 8px;
                  padding: 8px 12px;
                  font-size: 13px;
                  cursor: pointer;
                }

                .manage-button.active {
                  border-color: #d97706;
                  background: #fef3c7;
                  color: #92400e;
                }

                .manage-actions {
                  display: none;
                  align-items: center;
                  gap: 8px;
                  margin-bottom: 10px;
                }

                .manage-actions.visible {
                  display: flex;
                }

                .manage-select-all {
                  border: 1px solid #c8ccd4;
                  background: #ffffff;
                  color: #1f2937;
                  border-radius: 8px;
                  padding: 6px 10px;
                  font-size: 13px;
                  cursor: pointer;
                }

                .manage-delete {
                  border: 1px solid #dc2626;
                  background: #dc2626;
                  color: #ffffff;
                  border-radius: 8px;
                  padding: 6px 10px;
                  font-size: 13px;
                  cursor: pointer;
                }

                .manage-delete:disabled {
                  border-color: #e5e7eb;
                  background: #e5e7eb;
                  color: #9ca3af;
                  cursor: not-allowed;
                }

                .manage-hint {
                  font-size: 12px;
                  color: #6b7280;
                }

                .status {
                  font-size: 13px;
                  margin-bottom: 10px;
                  min-height: 18px;
                }

                .status.error {
                  color: #d93025;
                }

                .status.empty {
                  color: #6b7280;
                }

                .result-list {
                  display: flex;
                  flex-direction: column;
                  gap: 8px;
                }

                .result-row {
                  display: flex;
                  align-items: flex-start;
                  gap: 8px;
                }

                .result-select {
                  margin-top: 14px;
                }

                .result-item {
                  flex: 1;
                  border: 1px solid #d9dde5;
                  border-radius: 10px;
                  background: #ffffff;
                  padding: 10px;
                }

                .result-item .item-head {
                  display: flex;
                  justify-content: space-between;
                  align-items: center;
                  gap: 8px;
                  margin-bottom: 4px;
                }

                .result-item .branch {
                  font-size: 13px;
                  color: #2c6bed;
                  word-break: break-all;
                }

                .result-item .requirement {
                  font-size: 13px;
                  color: #1f2937;
                  word-break: break-all;
                }

                .copy-button {
                  border: 1px solid #c8ccd4;
                  background: #ffffff;
                  color: #1f2937;
                  border-radius: 6px;
                  font-size: 12px;
                  padding: 2px 8px;
                  cursor: pointer;
                  white-space: nowrap;
                }

                .modal-mask {
                  position: fixed;
                  inset: 0;
                  background: rgba(17, 24, 39, 0.32);
                  display: none;
                  align-items: center;
                  justify-content: center;
                  padding: 12px;
                }

                .modal-mask.visible {
                  display: flex;
                }

                .modal-panel {
                  width: min(420px, 100%);
                  background: #ffffff;
                  border-radius: 10px;
                  border: 1px solid #d9dde5;
                  padding: 14px;
                }

                .modal-title {
                  font-size: 14px;
                  font-weight: 600;
                  margin-bottom: 10px;
                }

                .modal-field {
                  display: flex;
                  flex-direction: column;
                  gap: 6px;
                  margin-bottom: 10px;
                }

                .modal-field label {
                  font-size: 12px;
                  color: #4b5563;
                }

                .modal-input {
                  border: 1px solid #c8ccd4;
                  border-radius: 8px;
                  padding: 8px 10px;
                  font-size: 13px;
                }

                .modal-actions {
                  display: flex;
                  justify-content: flex-end;
                  gap: 8px;
                  margin-top: 4px;
                }

                .modal-cancel {
                  border: 1px solid #c8ccd4;
                  background: #ffffff;
                  color: #1f2937;
                  border-radius: 8px;
                  padding: 6px 10px;
                  font-size: 13px;
                  cursor: pointer;
                }

                .modal-submit {
                  border: 1px solid #2c6bed;
                  background: #2c6bed;
                  color: #ffffff;
                  border-radius: 8px;
                  padding: 6px 10px;
                  font-size: 13px;
                  cursor: pointer;
                }
              </style>
            </head>
            <body>
              <div class="toolbar">
                <input id="keyword" class="search-input" placeholder="输入需求名或分支名" />
                <button id="reload" class="reload-button" type="button">刷新</button>
                <button id="add" class="add-button" type="button">新增</button>
                <button id="manage" class="manage-button" type="button">管理</button>
              </div>
              <div id="manageActions" class="manage-actions">
                <button id="selectAll" class="manage-select-all" type="button">全选</button>
                <button id="deleteSelected" class="manage-delete" type="button">删除</button>
                <span id="manageHint" class="manage-hint"></span>
              </div>
              <div id="status" class="status"></div>
              <div id="results" class="result-list"></div>
              <div id="addModal" class="modal-mask">
                <div class="modal-panel" role="dialog" aria-modal="true" aria-label="新增映射">
                  <div class="modal-title">新增</div>
                  <div class="modal-field">
                    <label for="branchName">分支名</label>
                    <input id="branchName" class="modal-input" type="text" placeholder="输入分支名" />
                  </div>
                  <div class="modal-field">
                    <label for="requirementName">需求名</label>
                    <input id="requirementName" class="modal-input" type="text" placeholder="输入需求名" />
                  </div>
                  <div class="modal-actions">
                    <button id="cancelAdd" class="modal-cancel" type="button">取消</button>
                    <button id="submitAdd" class="modal-submit" type="button">保存（Enter）</button>
                  </div>
                </div>
              </div>

              <script>
                const appState = {
                  status: 'EMPTY',
                  message: '',
                  items: [],
                  defaultBranchName: '',
                };

                const keywordInput = document.getElementById('keyword');
                const statusNode = document.getElementById('status');
                const resultsNode = document.getElementById('results');
                const addModal = document.getElementById('addModal');
                const branchInput = document.getElementById('branchName');
                const requirementInput = document.getElementById('requirementName');
                const manageActions = document.getElementById('manageActions');
                const manageButton = document.getElementById('manage');
                const selectAllButton = document.getElementById('selectAll');
                const deleteSelectedButton = document.getElementById('deleteSelected');
                const manageHint = document.getElementById('manageHint');
                const selectedBranchNames = new Set();
                let manageMode = false;

                function sendReload() {
                  const payload = 'reload';
                  ${queryInjection}
                }

                function sendAddMapping(branchName, requirementName) {
                  const payload = JSON.stringify({
                    command: 'addMapping',
                    branchName,
                    requirementName,
                  });
                  ${queryInjection}
                }

                function sendDeleteMappings(branchNames) {
                  const payload = JSON.stringify({
                    command: 'deleteMappings',
                    branchNames,
                  });
                  ${queryInjection}
                }

                function escapeRegex(input) {
                  return input.replace(/[.*+?^${'$'}{}()|[\]\\]/g, '\\${'$'}&');
                }

                function filterItems() {
                  const keyword = keywordInput.value.trim();
                  if (!keyword) {
                    return appState.items;
                  }

                  const matcher = new RegExp(escapeRegex(keyword), 'i');
                  return appState.items.filter((item) => {
                    return matcher.test(item.branchName) || matcher.test(item.requirementName);
                  });
                }

                function resetStatusClass() {
                  statusNode.classList.remove('error');
                  statusNode.classList.remove('empty');
                }

                function createResultItem(item) {
                  const row = document.createElement('div');
                  row.className = 'result-row';

                  if (manageMode) {
                    const checkbox = document.createElement('input');
                    checkbox.className = 'result-select';
                    checkbox.type = 'checkbox';
                    checkbox.checked = selectedBranchNames.has(item.branchName);
                    checkbox.addEventListener('change', () => {
                      if (checkbox.checked) {
                        selectedBranchNames.add(item.branchName);
                      } else {
                        selectedBranchNames.delete(item.branchName);
                      }
                      updateManageActions();
                    });
                    row.appendChild(checkbox);
                  }

                  const container = document.createElement('div');
                  container.className = 'result-item';

                  const head = document.createElement('div');
                  head.className = 'item-head';

                  const branch = document.createElement('div');
                  branch.className = 'branch';
                  branch.textContent = item.branchName;

                  const copyButton = document.createElement('button');
                  copyButton.className = 'copy-button';
                  copyButton.type = 'button';
                  copyButton.textContent = '复制分支名';
                  copyButton.addEventListener('click', () => {
                    copyBranchName(item.branchName);
                  });

                  const requirement = document.createElement('div');
                  requirement.className = 'requirement';
                  requirement.textContent = item.requirementName;

                  head.appendChild(branch);
                  head.appendChild(copyButton);
                  container.appendChild(head);
                  container.appendChild(requirement);
                  row.appendChild(container);
                  return row;
                }

                function fallbackCopyText(value) {
                  const tempTextArea = document.createElement('textarea');
                  tempTextArea.value = value;
                  tempTextArea.style.position = 'fixed';
                  tempTextArea.style.opacity = '0';
                  document.body.appendChild(tempTextArea);
                  tempTextArea.focus();
                  tempTextArea.select();
                  const copied = document.execCommand('copy');
                  document.body.removeChild(tempTextArea);
                  return copied;
                }

                async function copyBranchName(branchName) {
                  try {
                    if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
                      await navigator.clipboard.writeText(branchName);
                    } else {
                      fallbackCopyText(branchName);
                    }
                  } catch (error) {
                    fallbackCopyText(branchName);
                  }
                }

                function openAddModal() {
                  requirementInput.value = '';
                  const defaultBranchName = (appState.defaultBranchName || '').trim();
                  if (defaultBranchName) {
                    branchInput.value = defaultBranchName;
                    branchInput.placeholder = '默认当前分支名';
                  } else {
                    branchInput.value = '';
                    branchInput.placeholder = '输入分支名';
                  }
                  addModal.classList.add('visible');
                  if (defaultBranchName) {
                    requirementInput.focus();
                  } else {
                    branchInput.focus();
                  }
                }

                function closeAddModal() {
                  addModal.classList.remove('visible');
                }

                function submitAddMapping() {
                  const branchName = branchInput.value.trim();
                  const requirementName = requirementInput.value.trim();
                  if (!branchName || !requirementName) {
                    if (!branchName) {
                      branchInput.focus();
                    } else {
                      requirementInput.focus();
                    }
                    return;
                  }

                  sendAddMapping(branchName, requirementName);
                  closeAddModal();
                }

                function toggleManageMode() {
                  manageMode = !manageMode;
                  if (!manageMode) {
                    selectedBranchNames.clear();
                  }
                  render();
                }

                function updateManageActions() {
                  manageActions.classList.toggle('visible', manageMode);
                  manageButton.classList.toggle('active', manageMode);
                  manageButton.textContent = manageMode ? '退出管理' : '管理';
                  const selectedCount = selectedBranchNames.size;
                  deleteSelectedButton.disabled = selectedCount === 0;
                  manageHint.textContent = manageMode ? `已选择 ${'$'}{selectedCount} 条` : '';
                }

                function selectAllMatched() {
                  const matched = filterItems();
                  const isAllMatchedSelected = matched.length > 0 && matched.every((item) => {
                    return selectedBranchNames.has(item.branchName);
                  });

                  if (isAllMatchedSelected) {
                    matched.forEach((item) => {
                      selectedBranchNames.delete(item.branchName);
                    });
                  } else {
                    matched.forEach((item) => {
                      selectedBranchNames.add(item.branchName);
                    });
                  }
                  render();
                }

                function deleteSelected() {
                  const branchNames = Array.from(selectedBranchNames);
                  if (branchNames.length === 0) {
                    return;
                  }

                  sendDeleteMappings(branchNames);
                  selectedBranchNames.clear();
                  render();
                }

                function render() {
                  resetStatusClass();
                  resultsNode.innerHTML = '';

                  const existingBranchNames = new Set(appState.items.map((item) => item.branchName));
                  Array.from(selectedBranchNames).forEach((branchName) => {
                    if (!existingBranchNames.has(branchName)) {
                      selectedBranchNames.delete(branchName);
                    }
                  });
                  updateManageActions();

                  if (appState.status === 'ERROR') {
                    statusNode.textContent = appState.message || '读取文件失败';
                    statusNode.classList.add('error');
                    return;
                  }

                  if (appState.status === 'EMPTY') {
                    statusNode.textContent = appState.message || '暂无映射数据';
                    statusNode.classList.add('empty');
                    return;
                  }

                  const matched = filterItems();
                  if (matched.length === 0) {
                    statusNode.textContent = '共 0 条映射';
                    selectAllButton.textContent = '全选';
                    return;
                  }

                  const isAllMatchedSelected = matched.every((item) => {
                    return selectedBranchNames.has(item.branchName);
                  });
                  selectAllButton.textContent = isAllMatchedSelected ? '取消全选' : '全选';

                  const countText = `共 ${'$'}{matched.length} 条映射`;
                  statusNode.textContent = countText;
                  matched.forEach((item) => {
                    resultsNode.appendChild(createResultItem(item));
                  });
                }

                keywordInput.addEventListener('input', render);
                document.getElementById('reload').addEventListener('click', sendReload);
                document.getElementById('add').addEventListener('click', openAddModal);
                document.getElementById('manage').addEventListener('click', toggleManageMode);
                document.getElementById('selectAll').addEventListener('click', selectAllMatched);
                document.getElementById('deleteSelected').addEventListener('click', deleteSelected);
                document.getElementById('cancelAdd').addEventListener('click', closeAddModal);
                document.getElementById('submitAdd').addEventListener('click', submitAddMapping);
                [branchInput, requirementInput].forEach((inputNode) => {
                  inputNode.addEventListener('keydown', (event) => {
                    if (event.key === 'Enter') {
                      event.preventDefault();
                      submitAddMapping();
                    }
                  });
                });
                addModal.addEventListener('click', (event) => {
                  if (event.target === addModal) {
                    closeAddModal();
                  }
                });

                window.BranchMappingApp = {
                  setState(nextState) {
                    appState.status = nextState.status;
                    appState.message = nextState.message || '';
                    appState.items = Array.isArray(nextState.items) ? nextState.items : [];
                    appState.defaultBranchName = nextState.defaultBranchName || '';
                    render();
                  },
                };

                render();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private companion object {
        private const val RELOAD_COMMAND = "reload"
        private const val ADD_MAPPING_COMMAND = "addMapping"
        private const val DELETE_MAPPINGS_COMMAND = "deleteMappings"
    }

    private data class AddMappingCommand(
        val branchName: String,
        val requirementName: String,
    )
}
