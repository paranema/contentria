import {
  $createCodeBlockNode,
  createRootEditorSubscription$,
  realmPlugin,
} from '@mdxeditor/editor';
import {
  $getSelection,
  $isParagraphNode,
  $isRangeSelection,
  COMMAND_PRIORITY_HIGH,
  KEY_ENTER_COMMAND,
} from 'lexical';

/** 언어 지정이 선택적인 코드 펜스(```lang) 패턴입니다. */
const CODE_FENCE = /^```([\w-]*)$/;

/**
 * 코드 펜스(```) 문단에서 Enter 키를 누르면 코드 블록으로 변환하는 플러그인입니다.
 * 기본 마크다운 단축키는 공백 입력만 감지하므로 KEY_ENTER_COMMAND를 가로채 동일하게 변환하며,
 * 코드 블록을 포함할 수 없는 중첩 에디터를 제외하고 루트 에디터에만 등록합니다.
 */
export const codeFenceOnEnterPlugin = realmPlugin({
  init(realm) {
    realm.pub(createRootEditorSubscription$, (editor) =>
      editor.registerCommand(
        KEY_ENTER_COMMAND,
        (event) => {
          if (event?.shiftKey) {
            // Shift+Enter 줄바꿈은 기본 동작을 유지합니다.
            return false;
          }

          const selection = $getSelection();
          if (!$isRangeSelection(selection) || !selection.isCollapsed()) {
            return false;
          }

          const paragraph = selection.anchor.getNode().getTopLevelElement();
          if (!$isParagraphNode(paragraph)) {
            return false;
          }

          const match = CODE_FENCE.exec(paragraph.getTextContent());
          if (!match) {
            return false;
          }

          event?.preventDefault();
          const codeBlockNode = $createCodeBlockNode({
            code: '',
            language: match[1] ?? '',
            meta: '',
          });
          paragraph.replace(codeBlockNode);
          // CodeMirror 인스턴스가 마운트된 뒤 코드 블록 내부로 포커스를 이동합니다.
          setTimeout(() => codeBlockNode.select(), 80);
          return true;
        },
        COMMAND_PRIORITY_HIGH
      )
    );
  },
});
