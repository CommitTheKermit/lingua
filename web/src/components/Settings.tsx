import { useState, type CSSProperties } from "react";
import { Check, Minus, Plus } from "lucide-react";
import { Modal } from "./Modal";
import type {
  DisplaySettings,
  DisplayStyle,
  DisplayTarget,
} from "../domain/reader";
export function textStyle(style: DisplayStyle): CSSProperties {
  return {
    fontFamily:
      style.font === "serif"
        ? 'Georgia, "Noto Sans KR", serif'
        : style.font === "mono"
          ? 'ui-monospace, "Noto Sans KR", monospace'
          : '"Noto Sans KR", sans-serif',
    fontSize: `${style.size / 16}rem`,
    lineHeight: style.lineHeight,
    color: style.color,
    backgroundColor: style.background,
  };
}
const targets: { key: DisplayTarget; label: string }[] = [
  { key: "original", label: "원문" },
  { key: "guide", label: "사전 안내" },
  { key: "translation", label: "번역 입력" },
];
const backgrounds = [
  "#f8f9fa",
  "#ffffff",
  "#4a4a4a",
  "#2a2a2a",
  "#e9e5da",
  "#e4d0be",
  "#c3b083",
  "#cfced3",
  "#d1dcea",
];
const foregrounds = [
  "#181b1e",
  "#ffffff",
  "#495057",
  "#7b6755",
  "#645636",
  "#514d63",
  "#465568",
];
export function Settings({
  settings,
  viewer,
  onChange,
  onClose,
}: {
  settings: DisplaySettings;
  viewer: boolean;
  onChange: (target: DisplayTarget, style: DisplayStyle) => void;
  onClose: () => void;
}) {
  const [target, setTarget] = useState<DisplayTarget>(
    viewer ? "viewer" : "original",
  );
  const current = settings[target];
  const update = (part: Partial<DisplayStyle>) =>
    onChange(target, { ...current, ...part });
  return (
    <Modal title="읽기 옵션" onClose={onClose} className="settings-modal">
      <div className="setting-targets" aria-label="설정할 영역">
        {(viewer
          ? [{ key: "viewer" as const, label: "읽기 본문" }]
          : targets
        ).map((item) => (
          <button
            key={item.key}
            aria-pressed={item.key === target}
            className={item.key === target ? "selected" : ""}
            onClick={() => setTarget(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>
      <div className="settings-scroll">
        <section className="setting-preview">
          <p className="section-label">설정 미리보기</p>
          <div style={textStyle(current)}>
            한 문장씩, 나의 언어로.
            <br />
            <br />
            Read at your own pace.
            <br />
            Make every sentence your own.
          </div>
        </section>
        <section className="setting-group">
          <p className="section-label">글꼴과 색상</p>
          <label className="setting-row">
            폰트 선택
            <select
              aria-label="폰트 선택"
              value={current.font}
              onChange={(event) =>
                update({ font: event.target.value as DisplayStyle["font"] })
              }
            >
              <option value="sans">Noto Sans KR</option>
              <option value="serif">Serif</option>
              <option value="mono">Monospace</option>
            </select>
          </label>
          <div className="setting-row">
            <span>배경색</span>
            <div className="swatches">
              {backgrounds.map((color) => (
                <button
                  key={color}
                  aria-label={`배경색 ${color}`}
                  aria-pressed={current.background === color}
                  style={{ background: color }}
                  onClick={() => update({ background: color })}
                >
                  {current.background === color && (
                    <Check
                      size={18}
                      color={
                        ["#4a4a4a", "#2a2a2a"].includes(color)
                          ? "#fff"
                          : "#181b1e"
                      }
                    />
                  )}
                </button>
              ))}
            </div>
          </div>
          <div className="setting-row">
            <span>글자색</span>
            <div className="swatches">
              {foregrounds.map((color) => (
                <button
                  key={color}
                  aria-label={`글자색 ${color}`}
                  aria-pressed={current.color === color}
                  style={{ background: color }}
                  onClick={() => update({ color })}
                >
                  {current.color === color && (
                    <Check
                      size={18}
                      color={color === "#ffffff" ? "#181b1e" : "#fff"}
                    />
                  )}
                </button>
              ))}
            </div>
          </div>
        </section>
        <section className="setting-group">
          <p className="section-label">본문 설정</p>
          <div className="setting-row">
            <span>글자 크기</span>
            <div className="stepper">
              <button
                aria-label="글자 크기 줄이기"
                disabled={current.size <= 14}
                onClick={() => update({ size: current.size - 1 })}
              >
                <Minus size={18} />
              </button>
              <output aria-label="글자 크기">{current.size}</output>
              <button
                aria-label="글자 크기 늘리기"
                disabled={current.size >= 40}
                onClick={() => update({ size: current.size + 1 })}
              >
                <Plus size={18} />
              </button>
            </div>
          </div>
          <div className="setting-row">
            <span>줄 간격</span>
            <div className="stepper">
              <button
                aria-label="줄 간격 줄이기"
                disabled={current.lineHeight <= 1.2}
                onClick={() =>
                  update({
                    lineHeight:
                      Math.round((current.lineHeight - 0.1) * 10) / 10,
                  })
                }
              >
                <Minus size={18} />
              </button>
              <output>{current.lineHeight.toFixed(1)}</output>
              <button
                aria-label="줄 간격 늘리기"
                disabled={current.lineHeight >= 2.5}
                onClick={() =>
                  update({
                    lineHeight:
                      Math.round((current.lineHeight + 0.1) * 10) / 10,
                  })
                }
              >
                <Plus size={18} />
              </button>
            </div>
          </div>
        </section>
      </div>
      <footer className="modal-actions">
        <span className="muted">변경 사항은 자동 저장됩니다</span>
        <button className="primary-button" onClick={onClose}>
          완료
        </button>
      </footer>
    </Modal>
  );
}
