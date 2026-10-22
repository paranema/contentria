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

/** ```lang 패턴 (언어는 선택) — markdownShortcutPlugin의 스페이스 트리거와 동일한 문법. */
const CODE_FENCE = /^```([\w-]*)$/;

/**
 * "```" 문단에서 Enter를 누르면 코드 블록으로 변환하는 커스텀 플러그인.
 *
 * lexical의 마크다운 변환 러너는 "스페이스 입력"만 트리거하지만(Notion/Obsidian은
 * Enter도 받아준다), 이 플러그인이 KEY_ENTER_COMMAND를 가로채 같은 변환을 수행한다.
 * 치환 로직은 @mdxeditor/editor markdown-shortcut 플러그인 내부 구현과 동일하게 맞췄다.
 *
 * 루트 에디터에만 등록한다 — 테이블 셀 같은 중첩 에디터는 코드 블록을 담을 수 없다.
 */
export const codeFenceOnEnterPlugin = realmPlugin({
  init(realm) {
    realm.pub(createRootEditorSubscription$, (editor) =>
      editor.registerCommand(
        KEY_ENTER_COMMAND,
        (event) => {
          if (event?.shiftKey) {
            return false; // soft break는 그대로 둔다
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
          // markdown-shortcut 플러그인과 동일: CodeMirror 마운트 후 포커스를 옮긴다.
          setTimeout(() => codeBlockNode.select(), 80);
          return true;
        },
        COMMAND_PRIORITY_HIGH
      )
    );
  },
});
