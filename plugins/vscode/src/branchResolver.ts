import { execFile } from "node:child_process";
import { promisify } from "node:util";

import * as vscode from "vscode";

import {
  GIT_BRANCH_ARGS,
  GIT_COMMAND,
  GIT_COMMAND_TIMEOUT_MS,
} from "./constants";

const execFileAsync = promisify(execFile);

export async function resolveCurrentBranchName(): Promise<string> {
  const workspaceFolder = vscode.workspace.workspaceFolders?.[0];

  if (!workspaceFolder) {
    return "";
  }

  try {
    const { stdout } = await execFileAsync(GIT_COMMAND, [...GIT_BRANCH_ARGS], {
      cwd: workspaceFolder.uri.fsPath,
      timeout: GIT_COMMAND_TIMEOUT_MS,
    });
    const branchName = stdout.trim();

    if (!branchName || branchName === "HEAD") {
      return "";
    }

    return branchName;
  } catch {
    return "";
  }
}
