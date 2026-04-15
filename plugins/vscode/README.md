# Git Task Branch Mapper

Git Task Branch Mapper helps you keep a searchable mapping between requirement names and Git branches inside VS Code.

## Features

- Search mappings by requirement name or branch name.
- Create mappings with the current branch prefilled by default.
- Copy branch names with one click.
- Batch delete mappings in management mode.
- Read and write desktop `git-branch-mapping.json` immediately after each change.

## Mapping File

Create `git-branch-mapping.json` on your desktop before using the extension.

- macOS: `~/Desktop/git-branch-mapping.json`
- Windows: `%USERPROFILE%\\Desktop\\git-branch-mapping.json`
- OneDrive Desktop is also supported on Windows.

Example:

```json
[
  {
    "branchName": "feature/task-123",
    "requirementName": "Task 123 user login polish"
  }
]
```

## Usage

1. Open the `Branch Mapper` view from the activity bar.
2. Click `Refresh` to load the latest mappings.
3. Click `Add` to create a new requirement-to-branch mapping.
4. Use management mode when you want to delete multiple mappings at once.

## Release Notes

See [`CHANGELOG.md`](./CHANGELOG.md) for recent updates.
