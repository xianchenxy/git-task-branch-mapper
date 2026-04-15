import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";

import { MAPPING_FILE_NAME } from "./constants";
import type {
  BranchMappingItem,
  LoadMappingsResult,
  SaveMappingsResult,
} from "./types";

interface MappingValue {
  requirementName: string;
  updatedAt: string;
}

type MappingRecord = Record<string, MappingValue>;

export class BranchMappingRepository {
  async load(): Promise<LoadMappingsResult> {
    try {
      const filePath = await this.resolveMappingFilePath();
      const items = await this.readItems(filePath);
      if (items.length === 0) {
        return { type: "empty", message: "暂无映射数据" };
      }
      return { type: "success", items };
    } catch (error) {
      return { type: "error", message: this.toMessage("读取文件失败", error) };
    }
  }

  async saveMapping(branchName: string, requirementName: string): Promise<SaveMappingsResult> {
    try {
      const filePath = await this.resolveMappingFilePath();
      const current = await this.readRecord(filePath, true);
      const next: MappingRecord = {
        [branchName]: {
          requirementName,
          updatedAt: new Date().toISOString(),
        },
      };

      for (const [currentBranchName, currentValue] of Object.entries(current)) {
        if (currentBranchName !== branchName) {
          next[currentBranchName] = currentValue;
        }
      }

      await fs.writeFile(filePath, JSON.stringify(next, null, 2), "utf8");
      return { type: "success", affectedCount: 1 };
    } catch (error) {
      return { type: "error", message: this.toMessage("写入文件失败", error) };
    }
  }

  async deleteMappings(branchNames: string[]): Promise<SaveMappingsResult> {
    if (branchNames.length === 0) {
      return { type: "error", message: "删除失败：未选择要删除的映射" };
    }

    try {
      const filePath = await this.resolveMappingFilePath();
      const targetSet = new Set(branchNames);
      const current = await this.readRecord(filePath, false);
      const next: MappingRecord = {};
      let removedCount = 0;

      for (const [branchName, value] of Object.entries(current)) {
        if (targetSet.has(branchName)) {
          removedCount += 1;
        } else {
          next[branchName] = value;
        }
      }

      if (removedCount === 0) {
        return { type: "error", message: "删除失败：未找到选中的映射" };
      }

      await fs.writeFile(filePath, JSON.stringify(next, null, 2), "utf8");
      return { type: "success", affectedCount: removedCount };
    } catch (error) {
      return { type: "error", message: this.toMessage("写入文件失败", error) };
    }
  }

  private async resolveMappingFilePath(): Promise<string> {
    const userHome = os.homedir();

    if (!userHome) {
      throw new Error("无法定位用户目录");
    }

    const candidates = [path.join(userHome, "Desktop")];

    if (process.platform === "win32") {
      candidates.push(path.join(userHome, "OneDrive", "Desktop"));
    }

    for (const desktopDir of candidates) {
      try {
        await fs.access(desktopDir);
        return path.join(desktopDir, MAPPING_FILE_NAME);
      } catch {
        continue;
      }
    }

    return path.join(candidates[0], MAPPING_FILE_NAME);
  }

  private async readItems(filePath: string): Promise<BranchMappingItem[]> {
    const record = await this.readRecord(filePath, false);
    return Object.entries(record).map(([branchName, value]) => ({
      branchName,
      requirementName: value.requirementName,
      updatedAt: value.updatedAt,
    }));
  }

  private async readRecord(filePath: string, allowMissingFile: boolean): Promise<MappingRecord> {
    try {
      const content = (await fs.readFile(filePath, "utf8")).trim();

      if (!content) {
        return {};
      }

      const rawRecord = JSON.parse(content) as unknown;
      return this.normalizeRecord(rawRecord);
    } catch (error) {
      if (allowMissingFile && this.isMissingFileError(error)) {
        return {};
      }

      throw error;
    }
  }

  private normalizeRecord(rawRecord: unknown): MappingRecord {
    if (!this.isPlainObject(rawRecord)) {
      throw new Error("映射文件格式错误：根节点必须是对象");
    }

    return Object.fromEntries(
      Object.entries(rawRecord).map(([branchName, value]) => {
        if (typeof value === "string") {
          return [
            branchName,
            {
              requirementName: value,
              updatedAt: "",
            },
          ];
        }

        if (!this.isPlainObject(value)) {
          throw new Error(`映射文件格式错误：${branchName} 的值必须是字符串或对象`);
        }

        const requirementName = value.requirementName;
        if (typeof requirementName !== "string" || !requirementName) {
          throw new Error(`映射文件格式错误：${branchName} 缺少 requirementName`);
        }

        const updatedAt = value.updatedAt;
        return [
          branchName,
          {
            requirementName,
            updatedAt: typeof updatedAt === "string" ? updatedAt : "",
          },
        ];
      }),
    );
  }

  private isPlainObject(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null && !Array.isArray(value);
  }

  private isMissingFileError(error: unknown): error is NodeJS.ErrnoException {
    return Boolean(error && typeof error === "object" && "code" in error && error.code === "ENOENT");
  }

  private toMessage(prefix: string, error: unknown): string {
    const message = error instanceof Error ? error.message : "未知错误";
    return `${prefix}：${message}`;
  }
}
