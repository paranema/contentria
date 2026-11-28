import type { Translation } from '@mdxeditor/editor';

// 운영체제에 맞춰 단축키 안내 표기(⌘Z / Ctrl+Z)를 분기합니다.
const IS_APPLE =
  typeof navigator !== 'undefined' && /Mac|iPod|iPhone|iPad/.test(navigator.platform);
const mod = (key: string) => (IS_APPLE ? `⌘${key}` : `Ctrl+${key}`);

/**
 * MDXEditor UI 문자열의 한국어 번역 사전입니다.
 * 사전에 없는 키는 라이브러리의 영문 기본값(defaultValue)으로 대체됩니다.
 */
const KO: Record<string, string> = {
  // 서식 토글 툴바 항목입니다. 실제 단축키가 등록된 항목에만 단축키 안내를 함께 표시합니다.
  'toolbar.undo': '실행 취소 {{shortcut}}',
  'toolbar.redo': '다시 실행 {{shortcut}}',
  'toolbar.bold': `굵게 (${mod('B')})`,
  'toolbar.removeBold': `굵게 해제 (${mod('B')})`,
  'toolbar.italic': `기울임 (${mod('I')})`,
  'toolbar.removeItalic': `기울임 해제 (${mod('I')})`,
  'toolbar.underline': `밑줄 (${mod('U')})`,
  'toolbar.removeUnderline': `밑줄 해제 (${mod('U')})`,
  'toolbar.strikethrough': '취소선',
  'toolbar.removeStrikethrough': '취소선 해제',
  'toolbar.superscript': '위 첨자',
  'toolbar.removeSuperscript': '위 첨자 해제',
  'toolbar.subscript': '아래 첨자',
  'toolbar.removeSubscript': '아래 첨자 해제',
  'toolbar.highlight': '형광펜',
  'toolbar.removeHighlight': '형광펜 해제',
  'toolbar.inlineCode': '인라인 코드',
  'toolbar.removeInlineCode': '인라인 코드 해제',

  // 삽입 툴바 항목입니다.
  'toolbar.link': `링크 삽입 (${mod('K')})`,
  'toolbar.image': '이미지 삽입',
  'toolbar.table': '테이블 삽입',
  'toolbar.codeBlock': '코드 블록 삽입',
  'toolbar.admonition': '알림 상자 삽입',
  'toolbar.thematicBreak': '구분선 삽입',
  'toolbar.insertFrontmatter': '프런트매터 삽입',
  'toolbar.editFrontmatter': '프런트매터 편집',

  // 목록 및 블록 유형 툴바 항목입니다.
  'toolbar.bulletedList': '글머리 기호 목록',
  'toolbar.numberedList': '번호 목록',
  'toolbar.checkList': '체크리스트',
  'toolbar.blockTypeSelect.placeholder': '블록 유형',
  'toolbar.blockTypeSelect.selectBlockTypeTooltip': '블록 유형 선택',
  'toolbar.blockTypes.paragraph': '본문',
  'toolbar.blockTypes.quote': '인용문',
  'toolbar.blockTypes.heading': '제목 {{level}}',

  // 보기 모드 전환 툴바 항목입니다.
  'toolbar.richText': '리치 텍스트',
  'toolbar.source': '소스 모드',
  'toolbar.diffMode': '변경 비교',
  'toolbar.toggleGroup': '토글 그룹',

  // 알림 상자(admonition) 항목입니다.
  'admonitions.note': '노트',
  'admonitions.tip': '팁',
  'admonitions.info': '정보',
  'admonitions.caution': '주의',
  'admonitions.danger': '위험',
  'admonitions.changeType': '알림 상자 유형 선택',
  'admonitions.placeholder': '알림 상자 유형',

  // 코드 블록 항목입니다.
  'codeBlock.language': '코드 블록 언어',
  'codeBlock.selectLanguage': '코드 블록 언어 선택',
  'codeblock.delete': '코드 블록 삭제',

  // 링크 대화상자 및 미리보기 항목입니다.
  'createLink.url': 'URL',
  'createLink.urlPlaceholder': 'URL을 선택하거나 붙여넣으세요',
  'createLink.text': '링크 텍스트',
  'createLink.textTooltip': '링크에 표시될 텍스트',
  'createLink.title': '링크 제목',
  'createLink.titleTooltip': '마우스 오버 시 표시되는 title 속성',
  'createLink.saveTooltip': 'URL 적용',
  'createLink.cancelTooltip': '변경 취소',
  'linkPreview.copyToClipboard': '클립보드에 복사',
  'linkPreview.copied': '복사됨!',
  'linkPreview.edit': '링크 URL 편집',
  'linkPreview.remove': '링크 제거',

  // 대화상자 공통 버튼 항목입니다.
  'dialogControls.save': '저장',
  'dialogControls.cancel': '취소',

  // 테이블 편집 메뉴 항목입니다.
  'table.columnMenu': '열 메뉴',
  'table.rowMenu': '행 메뉴',
  'table.textAlignment': '텍스트 정렬',
  'table.alignLeft': '왼쪽 정렬',
  'table.alignCenter': '가운데 정렬',
  'table.alignRight': '오른쪽 정렬',
  'table.insertColumnLeft': '왼쪽에 열 삽입',
  'table.insertColumnRight': '오른쪽에 열 삽입',
  'table.insertRowAbove': '위에 행 삽입',
  'table.insertRowBelow': '아래에 행 삽입',
  'table.deleteColumn': '이 열 삭제',
  'table.deleteRow': '이 행 삭제',
  'table.deleteTable': '테이블 삭제',

  // 이미지 편집 및 업로드 대화상자 항목입니다.
  'imageEditor.editImage': '이미지 편집',
  'imageEditor.deleteImage': '이미지 삭제',
  'uploadImage.dialogTitle': '이미지 업로드',
  'uploadImage.uploadInstructions': '기기에서 이미지 업로드:',
  'uploadImage.addViaUrlInstructions': '또는 URL로 이미지 추가:',
  'uploadImage.addViaUrlInstructionsNoUpload': 'URL로 이미지 추가:',
  'uploadImage.autoCompletePlaceholder': '이미지 주소를 선택하거나 붙여넣으세요',
  'uploadImage.alt': '대체 텍스트:',
  'uploadImage.title': '제목:',
  'uploadImage.width': '너비:',
  'uploadImage.height': '높이:',

  // 본문 편집 영역의 스크린 리더 라벨입니다.
  'contentArea.editableMarkdown': '편집 가능한 마크다운',
};

/** `{{shortcut}}`, `{{level}}` 등의 변수 치환을 지원하는 MDXEditor 한국어 번역 함수입니다. */
export const mdxEditorKoTranslation: Translation = (key, defaultValue, interpolations) => {
  let value = KO[key] ?? defaultValue;
  if (interpolations) {
    for (const [name, raw] of Object.entries(interpolations)) {
      value = value.replaceAll(`{{${name}}}`, String(raw));
    }
  }
  return value;
};
