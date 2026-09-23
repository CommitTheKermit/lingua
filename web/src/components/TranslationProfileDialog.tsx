import { useState } from "react";
import { Modal } from "./Modal";
import {
  translationProfilePresets,
  type TranslationProfile,
  type TranslationProfileId,
} from "../domain/translationProfile";

const CONTEXT_LIMIT = 5000;
const INSTRUCTIONS_LIMIT = 300;

export function TranslationProfileDialog({
  profile,
  onApply,
  onClose,
}: {
  profile: TranslationProfile;
  onApply: (profile: TranslationProfile) => void;
  onClose: () => void;
}) {
  const [draft, setDraft] = useState<TranslationProfile>({ ...profile });
  const selectedPreset = translationProfilePresets.find(
    (preset) => preset.presetId === draft.presetId,
  );

  function selectPreset(presetId: TranslationProfileId) {
    const preset = translationProfilePresets.find(
      (item) => item.presetId === presetId,
    );
    if (preset) {
      setDraft({
        presetId: preset.presetId,
        context: preset.context,
        instructions: preset.instructions,
      });
    }
  }

  return (
    <Modal
      title="번역 프로필"
      description="세계관과 문체를 정하면 이 책의 문장 번역에 반영합니다."
      onClose={onClose}
      className="translation-profile-modal"
    >
      <div className="translation-profile-content">
        <section className="profile-preset-section" aria-labelledby="preset-label">
          <h3 id="preset-label">프리셋</h3>
          <div className="profile-preset-list">
            {translationProfilePresets.map((preset) => (
              <button
                key={preset.presetId}
                type="button"
                className="profile-preset"
                aria-pressed={draft.presetId === preset.presetId}
                onClick={() => selectPreset(preset.presetId)}
              >
                <strong>{preset.name}</strong>
                <span>{preset.summary}</span>
              </button>
            ))}
          </div>
        </section>

        <label className="profile-field">
          <span>세계관과 작품 정보</span>
          <textarea
            value={draft.context}
            maxLength={CONTEXT_LIMIT}
            rows={4}
            onChange={(event) =>
              setDraft({
                ...draft,
                presetId: "custom",
                context: event.target.value,
              })
            }
            placeholder="배경, 세력, 인물, 세계관 용어를 적어 주세요."
          />
          <small>{draft.context.length}/{CONTEXT_LIMIT}</small>
        </label>

        <label className="profile-field">
          <span>번역 어투와 지침</span>
          <textarea
            value={draft.instructions}
            maxLength={INSTRUCTIONS_LIMIT}
            rows={3}
            onChange={(event) =>
              setDraft({
                ...draft,
                presetId: "custom",
                instructions: event.target.value,
              })
            }
            placeholder="예: 암울하고 엄숙한 소설체로, 계급과 군사용어를 일관되게 유지"
          />
          <small>{draft.instructions.length}/{INSTRUCTIONS_LIMIT}</small>
        </label>
        <p className="profile-note">
          {selectedPreset?.presetId === "default"
            ? "기본 번역을 사용합니다. 설정은 이 브라우저에서 책별로 저장됩니다."
            : "현재 문장을 새 설정으로 다시 번역하며 앱 번역 한도를 사용할 수 있습니다."}
        </p>
      </div>
      <div className="modal-actions profile-actions">
        <span className="muted">이 브라우저에서 책별 저장</span>
        <div>
          <button type="button" className="text-button" onClick={onClose}>
            취소
          </button>
          <button
            type="button"
            className="primary-button"
            onClick={() => onApply(draft)}
          >
            적용
          </button>
        </div>
      </div>
    </Modal>
  );
}
