export const VIEW_ID = "gitTaskBranchMapper.view";
export const COMMAND_REFRESH = "gitTaskBranchMapper.refresh";
export const MAPPING_FILE_NAME = "git-branch-mapping.json";
export const GIT_COMMAND = "git";
export const GIT_BRANCH_ARGS = ["rev-parse", "--abbrev-ref", "HEAD"] as const;
export const GIT_COMMAND_TIMEOUT_MS = 2000;
