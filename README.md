# Git Task Branch Mapper

在多任务并行开发过程中，开发分支命名与需求/缺陷名称缺乏明确映射关系，导致后续回溯处理某项需求/缺陷时，难以快速定位其对应的分支名称。

## 下载地址

- JetBrains 插件下载目录：[`releases/jetbrains`](./releases/jetbrains/)

## 打包并发布到下载目录

执行以下命令后，会自动构建并把插件 zip 复制到 `releases/jetbrains`：

```bash
./gradlew publishJetBrainsPackage
```

