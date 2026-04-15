# Git Task Branch Mapper

在多任务并行开发过程中，开发分支命名与需求/缺陷名称缺乏明确映射关系，导致后续回溯处理某项需求/缺陷时，难以快速定位其对应的分支名称。

## 工程目录

- `WebStorm` 工程：[`plugins/webstorm`](./plugins/webstorm/)
- `VSCode` 工程：[`plugins/vscode`](./plugins/vscode/)
- 共享资产目录：[`assets/logo`](./assets/)

## 下载地址

- JetBrains 插件下载目录：[`releases/jetbrains`](./releases/jetbrains/)
- VSCode 插件下载目录：[`releases/vscode`](./releases/vscode/)

## 许可协议

本项目使用 `MIT` 协议，详见 [`LICENSE`](./LICENSE)。

## WebStorm 本地构建与发布

执行以下命令后，会自动构建并把插件 zip 放到 `releases/jetbrains`：

```bash
cd plugins/webstorm
./gradlew buildPlugin -q
./gradlew publishJetBrainsPackage -q
```

## VSCode 本地构建与发布

执行以下命令后，会完成 `VSCode` 扩展校验、构建和打包，并把 `.vsix` 放到 `releases/vscode`：

```bash
cd plugins/vscode
pnpm install
pnpm lint
pnpm build
pnpm package
```
