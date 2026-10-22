import type { Translation } from '@mdxeditor/editor';

/**
 * MDXEditor 한글 번역 사전.
 *
 * MDXEditor는 모든 UI 문자열을 `translation(key, defaultValue, interpolations)`로
 * 조회한다. 키 목록은 설치된 @mdxeditor/editor 3.50.0 dist에서 추출했으며,
 * 사전에 없는 키는 영어 기본값(defaultValue)으로 폴백되므로 라이브러리 업데이트로
 * 키가 추가되어도 빈 라벨이 생기지 않는다.
 */
/**
 * MDXEditor의 undo/redo 툴팁과 같은 표기(⌘Z / Ctrl+Z)를 따른다.
 * SSR 중에는 navigator가 없으므로 가드 — 에디터 자체가 ssr:false라 실제 표기는 항상 클라이언트 기준.
 */
const IS_APPLE =
  typeof navigator !== 'undefined' && /Mac|iPod|iPhone|iPad/.test(navigator.platform);
const mod = (key: string) => (IS_APPLE ? `⌘${key}` : `Ctrl+${key}`);

const KO: Record<string, string> = {
  // 툴바 — 서식 토글. 단축키 힌트는 실제 등록된 키에만 붙인다:
  // ⌘/Ctrl+B·I·U는 lexical 코어, ⌘/Ctrl+K는 MDXEditor link-dialog가 처리.
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

  // 툴바 — 삽입류
  'toolbar.link': `링크 삽입 (${mod('K')})`,
  'toolbar.image': '이미지 삽입',
  'toolbar.table': '테이블 삽입',
  'toolbar.codeBlock': '코드 블록 삽입',
  'toolbar.admonition': '알림 상자 삽입',
  'toolbar.thematicBreak': '구분선 삽입',
  'toolbar.insertFrontmatter': '프런트매터 삽입',
  'toolbar.editFrontmatter': '프런트매터 편집',

  // 툴바 — 목록/블록 유형
  'toolbar.bulletedList': '글머리 기호 목록',
  'toolbar.numberedList': '번호 목록',
  'toolbar.checkList': '체크리스트',
  'toolbar.blockTypeSelect.placeholder': '블록 유형',
  'toolbar.blockTypeSelect.selectBlockTypeTooltip': '블록 유형 선택',
  'toolbar.blockTypes.paragraph': '본문',
  'toolbar.blockTypes.quote': '인용문',
  'toolbar.blockTypes.heading': '제목 {{level}}',

  // 툴바 — 보기 모드
  'toolbar.richText': '리치 텍스트',
  'toolbar.source': '소스 모드',
  'toolbar.diffMode': '변경 비교',
  'toolbar.toggleGroup': '토글 그룹',

  // 알림 상자(admonition)
  'admonitions.note': '노트',
  'admonitions.tip': '팁',
  'admonitions.info': '정보',
  'admonitions.caution': '주의',
  'admonitions.danger': '위험',
  'admonitions.changeType': '알림 상자 유형 선택',
  'admonitions.placeholder': '알림 상자 유형',

  // 코드 블록
  'codeBlock.language': '코드 블록 언어',
  'codeBlock.selectLanguage': '코드 블록 언어 선택',
  'codeblock.delete': '코드 블록 삭제',

  // 링크 다이얼로그 / 프리뷰
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

  // 다이얼로그 공통
  'dialogControls.save': '저장',
  'dialogControls.cancel': '취소',

  // 테이블 편집 메뉴
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

  // 이미지 (업로드 다이얼로그는 CustomImageDialog가 대체하지만 인라인 편집 버튼은 스톡)
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

  // 본문 영역 (스크린 리더 라벨)
  'contentArea.editableMarkdown': '편집 가능한 마크다운',
};

/** `{{shortcut}}`, `{{level}}` 같은 머스태시 보간을 지원하는 번역 함수. */
export const mdxEditorKoTranslation: Translation = (key, defaultValue, interpolations) => {
  let value = KO[key] ?? defaultValue;
  if (interpolations) {
    for (const [name, raw] of Object.entries(interpolations)) {
      value = value.replaceAll(`{{${name}}}`, String(raw));
    }
  }
  return value;
};
