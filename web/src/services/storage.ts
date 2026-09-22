import { openDB } from "idb";
import {
  initialReader,
  SPLITTER_VERSION,
  type ReaderState,
} from "../domain/reader";
const database = () =>
  openDB("lingua-reader", 1, {
    upgrade(db) {
      db.createObjectStore("reader");
    },
  });
export async function restoreReader(): Promise<ReaderState> {
  const db = await database();
  try {
    const saved = await db.get("reader", "current");
    if (!saved) return initialReader();
    if (saved.version !== SPLITTER_VERSION || !saved.state?.settings)
      throw new Error("저장된 읽기 기록을 불러오지 못했습니다.");
    const settings = saved.state.settings;
    return {
      ...saved.state,
      settings: {
        ...settings,
        machineTranslation:
          settings.machineTranslation ??
          settings.guide ??
          initialReader().settings.machineTranslation,
      },
    };
  } finally {
    db.close();
  }
}
let saveQueue: Promise<void> = Promise.resolve();
export function persistReader(state: ReaderState): Promise<void> {
  saveQueue = saveQueue
    .catch(() => {})
    .then(async () => {
      const db = await database();
      try {
        await db.put("reader", { version: SPLITTER_VERSION, state }, "current");
      } finally {
        db.close();
      }
    });
  return saveQueue;
}
