import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { tags as t } from '@lezer/highlight';

/**
 * 에디터 코드 블록의 가독성을 높이기 위한 GitHub Light 계열 구문 강조 스타일입니다.
 * 기본 라이트 테마의 낮은 대비를 보완하며, 매칭되지 않는 토큰은 기본 텍스트 색상으로 표시됩니다.
 */
const editorHighlightStyle = HighlightStyle.define([
  { tag: [t.keyword, t.moduleKeyword, t.controlKeyword, t.operatorKeyword], color: '#cf222e' },
  { tag: [t.string, t.special(t.string), t.regexp], color: '#0a3069' },
  {
    tag: [t.comment, t.lineComment, t.blockComment, t.docComment],
    color: '#6e7781',
    fontStyle: 'italic',
  },
  { tag: [t.number, t.bool, t.null, t.atom, t.constant(t.variableName)], color: '#0550ae' },
  { tag: [t.function(t.variableName), t.function(t.propertyName)], color: '#8250df' },
  { tag: [t.className, t.typeName, t.namespace, t.definition(t.variableName)], color: '#953800' },
  { tag: [t.propertyName, t.attributeName], color: '#0550ae' },
  { tag: t.tagName, color: '#116329' },
  { tag: t.heading, fontWeight: 'bold' },
  { tag: t.link, color: '#0a3069', textDecoration: 'underline' },
]);

export const editorSyntaxHighlighting = syntaxHighlighting(editorHighlightStyle);
