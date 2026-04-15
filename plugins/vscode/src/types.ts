export interface BranchMappingItem {
  branchName: string;
  requirementName: string;
  updatedAt: string;
}

export type BranchMappingStatus = "READY" | "EMPTY" | "ERROR";

export interface BranchMappingViewState {
  status: BranchMappingStatus;
  message?: string;
  items: BranchMappingItem[];
  defaultBranchName: string;
}

export type LoadMappingsResult =
  | { type: "success"; items: BranchMappingItem[] }
  | { type: "empty"; message: string }
  | { type: "error"; message: string };

export type SaveMappingsResult =
  | { type: "success"; affectedCount: number }
  | { type: "error"; message: string };

export interface AddMappingMessage {
  command: "addMapping";
  branchName: string;
  requirementName: string;
}

export interface DeleteMappingsMessage {
  command: "deleteMappings";
  branchNames: string[];
}

export interface CopyBranchNameMessage {
  command: "copyBranchName";
  branchName: string;
}

export interface ReloadMessage {
  command: "reload";
}

export interface SetStateMessage {
  command: "setState";
  state: BranchMappingViewState;
}

export type WebviewMessage =
  | AddMappingMessage
  | DeleteMappingsMessage
  | CopyBranchNameMessage
  | ReloadMessage;
