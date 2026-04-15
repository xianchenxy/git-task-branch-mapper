export function getWebviewHtml(): string {
  const nonce = String(Date.now());

  return `<!DOCTYPE html>
  <html lang="zh-CN">
    <head>
      <meta charset="UTF-8" />
      <meta
        http-equiv="Content-Security-Policy"
        content="default-src 'none'; style-src 'unsafe-inline'; script-src 'nonce-${nonce}';"
      />
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
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
          min-width: 0;
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
        .item-head {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 8px;
          margin-bottom: 4px;
        }
        .branch {
          font-size: 13px;
          color: #2c6bed;
          word-break: break-all;
        }
        .requirement {
          font-size: 13px;
          color: #1f2937;
          word-break: break-all;
        }
        .updated-at {
          margin-top: 6px;
          font-size: 12px;
          color: #6b7280;
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

      <script nonce="${nonce}">
        const vscodeApi = acquireVsCodeApi();
        const appState = {
          status: "EMPTY",
          message: "",
          items: [],
          defaultBranchName: "",
        };
        const keywordInput = document.getElementById("keyword");
        const statusNode = document.getElementById("status");
        const resultsNode = document.getElementById("results");
        const addModal = document.getElementById("addModal");
        const branchInput = document.getElementById("branchName");
        const requirementInput = document.getElementById("requirementName");
        const manageActions = document.getElementById("manageActions");
        const manageButton = document.getElementById("manage");
        const selectAllButton = document.getElementById("selectAll");
        const deleteSelectedButton = document.getElementById("deleteSelected");
        const manageHint = document.getElementById("manageHint");
        const selectedBranchNames = new Set();
        let manageMode = false;

        function sendMessage(message) {
          vscodeApi.postMessage(message);
        }

        function escapeRegex(input) {
          return input.replace(/[.*+?^${"$"}{}()|[\\]\\\\]/g, "\\\\${"$"}&");
        }

        function filterItems() {
          const keyword = keywordInput.value.trim();
          if (!keyword) {
            return appState.items;
          }

          const matcher = new RegExp(escapeRegex(keyword), "i");
          return appState.items.filter((item) => {
            return matcher.test(item.branchName) || matcher.test(item.requirementName);
          });
        }

        function resetStatusClass() {
          statusNode.classList.remove("error");
          statusNode.classList.remove("empty");
        }

        function createResultItem(item) {
          const row = document.createElement("div");
          row.className = "result-row";

          if (manageMode) {
            const checkbox = document.createElement("input");
            checkbox.className = "result-select";
            checkbox.type = "checkbox";
            checkbox.checked = selectedBranchNames.has(item.branchName);
            checkbox.addEventListener("change", () => {
              if (checkbox.checked) {
                selectedBranchNames.add(item.branchName);
              } else {
                selectedBranchNames.delete(item.branchName);
              }
              updateManageActions();
            });
            row.appendChild(checkbox);
          }

          const container = document.createElement("div");
          container.className = "result-item";

          const head = document.createElement("div");
          head.className = "item-head";

          const branch = document.createElement("div");
          branch.className = "branch";
          branch.textContent = item.branchName;

          const copyButton = document.createElement("button");
          copyButton.className = "copy-button";
          copyButton.type = "button";
          copyButton.textContent = "复制分支名";
          copyButton.addEventListener("click", () => {
            sendMessage({
              command: "copyBranchName",
              branchName: item.branchName,
            });
          });

          const requirement = document.createElement("div");
          requirement.className = "requirement";
          requirement.textContent = item.requirementName;

          const updatedAt = document.createElement("div");
          updatedAt.className = "updated-at";
          updatedAt.textContent = formatUpdatedAt(item.updatedAt);

          head.appendChild(branch);
          head.appendChild(copyButton);
          container.appendChild(head);
          container.appendChild(requirement);
          container.appendChild(updatedAt);
          row.appendChild(container);

          return row;
        }

        function formatUpdatedAt(value) {
          if (!value) {
            return "更新时间：未知";
          }

          const date = new Date(value);
          if (Number.isNaN(date.getTime())) {
            return "更新时间：未知";
          }

          return "更新时间：" + new Intl.DateTimeFormat("zh-CN", {
            year: "numeric",
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit",
            second: "2-digit",
            hour12: false,
          }).format(date);
        }

        function openAddModal() {
          requirementInput.value = "";
          const defaultBranchName = (appState.defaultBranchName || "").trim();
          if (defaultBranchName) {
            branchInput.value = defaultBranchName;
            branchInput.placeholder = "默认当前分支名";
          } else {
            branchInput.value = "";
            branchInput.placeholder = "输入分支名";
          }
          addModal.classList.add("visible");
          if (defaultBranchName) {
            requirementInput.focus();
          } else {
            branchInput.focus();
          }
        }

        function closeAddModal() {
          addModal.classList.remove("visible");
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

          sendMessage({
            command: "addMapping",
            branchName,
            requirementName,
          });
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
          manageActions.classList.toggle("visible", manageMode);
          manageButton.classList.toggle("active", manageMode);
          manageButton.textContent = manageMode ? "退出管理" : "管理";
          const selectedCount = selectedBranchNames.size;
          deleteSelectedButton.disabled = selectedCount === 0;
          manageHint.textContent = manageMode ? \`已选择 \${selectedCount} 条\` : "";
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

          sendMessage({
            command: "deleteMappings",
            branchNames,
          });
          selectedBranchNames.clear();
          render();
        }

        function render() {
          resetStatusClass();
          resultsNode.innerHTML = "";

          const existingBranchNames = new Set(appState.items.map((item) => item.branchName));
          Array.from(selectedBranchNames).forEach((branchName) => {
            if (!existingBranchNames.has(branchName)) {
              selectedBranchNames.delete(branchName);
            }
          });
          updateManageActions();

          if (appState.status === "ERROR") {
            statusNode.textContent = appState.message || "读取文件失败";
            statusNode.classList.add("error");
            return;
          }

          if (appState.status === "EMPTY") {
            statusNode.textContent = appState.message || "暂无映射数据";
            statusNode.classList.add("empty");
            return;
          }

          const matched = filterItems();
          if (matched.length === 0) {
            statusNode.textContent = "共 0 条映射";
            selectAllButton.textContent = "全选";
            return;
          }

          const isAllMatchedSelected = matched.every((item) => {
            return selectedBranchNames.has(item.branchName);
          });
          selectAllButton.textContent = isAllMatchedSelected ? "取消全选" : "全选";
          statusNode.textContent = \`共 \${matched.length} 条映射\`;

          matched.forEach((item) => {
            resultsNode.appendChild(createResultItem(item));
          });
        }

        keywordInput.addEventListener("input", render);
        document.getElementById("reload").addEventListener("click", () => {
          sendMessage({ command: "reload" });
        });
        document.getElementById("add").addEventListener("click", openAddModal);
        manageButton.addEventListener("click", toggleManageMode);
        selectAllButton.addEventListener("click", selectAllMatched);
        deleteSelectedButton.addEventListener("click", deleteSelected);
        document.getElementById("cancelAdd").addEventListener("click", closeAddModal);
        document.getElementById("submitAdd").addEventListener("click", submitAddMapping);
        [branchInput, requirementInput].forEach((inputNode) => {
          inputNode.addEventListener("keydown", (event) => {
            if (event.key === "Enter") {
              event.preventDefault();
              submitAddMapping();
            }
          });
        });
        addModal.addEventListener("click", (event) => {
          if (event.target === addModal) {
            closeAddModal();
          }
        });
        window.addEventListener("message", (event) => {
          const message = event.data;
          if (!message || message.command !== "setState") {
            return;
          }
          appState.status = message.state.status;
          appState.message = message.state.message || "";
          appState.items = Array.isArray(message.state.items) ? message.state.items : [];
          appState.defaultBranchName = message.state.defaultBranchName || "";
          render();
        });
        render();
      </script>
    </body>
  </html>`;
}
