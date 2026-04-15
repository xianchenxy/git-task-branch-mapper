import * as vscode from "vscode";

import { resolveCurrentBranchName } from "./branchResolver";
import { COMMAND_REFRESH, VIEW_ID } from "./constants";
import { BranchMappingRepository } from "./mappingRepository";
import type {
  BranchMappingViewState,
  SetStateMessage,
  WebviewMessage,
} from "./types";
import { getWebviewHtml } from "./webview/getWebviewHtml";

class BranchMapperViewProvider implements vscode.WebviewViewProvider {
  private view?: vscode.WebviewView;
  private readonly repository = new BranchMappingRepository();

  async resolveWebviewView(webviewView: vscode.WebviewView): Promise<void> {
    this.view = webviewView;
    webviewView.webview.options = { enableScripts: true };
    webviewView.webview.html = getWebviewHtml();

    webviewView.webview.onDidReceiveMessage(async (message: WebviewMessage) => {
      await this.handleMessage(message);
    });

    await this.refresh();
  }

  async refresh(): Promise<void> {
    const [loadResult, defaultBranchName] = await Promise.all([
      this.repository.load(),
      resolveCurrentBranchName(),
    ]);

    let state: BranchMappingViewState;
    if (loadResult.type === "success") {
      state = {
        status: "READY",
        items: loadResult.items,
        defaultBranchName,
      };
    } else if (loadResult.type === "empty") {
      state = {
        status: "EMPTY",
        message: loadResult.message,
        items: [],
        defaultBranchName,
      };
    } else {
      state = {
        status: "ERROR",
        message: loadResult.message,
        items: [],
        defaultBranchName,
      };
    }

    this.postMessage({ command: "setState", state });
  }

  private async handleMessage(message: WebviewMessage): Promise<void> {
    if (message.command === "reload") {
      await this.refresh();
      return;
    }

    if (message.command === "addMapping") {
      await this.repository.saveMapping(
        message.branchName.trim(),
        message.requirementName.trim(),
      );
      await this.refresh();
      return;
    }

    if (message.command === "deleteMappings") {
      await this.repository.deleteMappings(message.branchNames);
      await this.refresh();
      return;
    }

    if (message.command === "copyBranchName") {
      await vscode.env.clipboard.writeText(message.branchName);
    }
  }

  private postMessage(message: SetStateMessage): void {
    void this.view?.webview.postMessage(message);
  }
}

export function activate(context: vscode.ExtensionContext): void {
  const provider = new BranchMapperViewProvider();

  context.subscriptions.push(
    vscode.window.registerWebviewViewProvider(VIEW_ID, provider),
    vscode.commands.registerCommand(COMMAND_REFRESH, async () => {
      await provider.refresh();
    }),
  );
}

export function deactivate(): void {}
