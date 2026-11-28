'use client';

import { ReactNode, useCallback, useRef, useState } from 'react';
import {
  AdmonitionDirectiveDescriptor,
  BlockTypeSelect,
  BoldItalicUnderlineToggles,
  ChangeAdmonitionType,
  ChangeCodeMirrorLanguage,
  codeBlockPlugin,
  CodeMirrorEditor,
  codeMirrorPlugin,
  CodeToggle,
  ConditionalContents,
  CreateLink,
  diffSourcePlugin,
  DiffSourceToggleWrapper,
  DirectiveNode,
  directivesPlugin,
  EditorInFocus,
  headingsPlugin,
  HighlightToggle,
  imagePlugin,
  InsertAdmonition,
  InsertCodeBlock,
  InsertTable,
  InsertThematicBreak,
  linkDialogPlugin,
  linkPlugin,
  listsPlugin,
  ListsToggle,
  markdownShortcutPlugin,
  MDXEditorMethods,
  quotePlugin,
  Separator,
  StrikeThroughSupSubToggles,
  tablePlugin,
  thematicBreakPlugin,
  toolbarPlugin,
  UndoRedo,
} from '@mdxeditor/editor';
import { ForwardRefEditor } from './ForwardRefEditor';
import CustomInsertImage, { ImageUploader } from './CustomInsertImage';
import CustomImageDialog from './CustomImageDialog';
import { editorSyntaxHighlighting } from './editorCodeHighlight';
import { codeFenceOnEnterPlugin } from './codeFenceOnEnterPlugin';
import { mdxEditorKoTranslation } from './mdxEditorKoTranslation';
import { Button } from '@/shared/ui/button';
import { Input } from '@/shared/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select';
import {
  publishPostAction,
  republishPostAction,
  revisePostAction,
  writePostAction,
} from '../../../api/posts';
import { publishingErrorMessage } from '../../../errors';
import { CategoryView, PostRequest, PostView } from '../../../types';
import { ArrowLeft, Loader2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { PATHS } from '@/shared/constants/paths';

type AdmonitionKind = 'note' | 'tip' | 'danger' | 'info' | 'caution';

function whenInAdmonition(editorInFocus: EditorInFocus | null) {
  const node = editorInFocus?.rootNode;
  if (!node || node.getType() !== 'directive') {
    return false;
  }

  return ['note', 'tip', 'danger', 'info', 'caution'].includes(
    (node as DirectiveNode).getMdastNode().name as AdmonitionKind
  );
}

interface PostEditorClientProps {
  blogSlug: string;
  categories: CategoryView[];
  /** 수정할 기존 글 데이터이며, 새 글 작성 시에는 전달하지 않습니다. */
  initialData?: PostView;
  /** 본문 이미지를 업로드하고 공개 URL을 반환하는 핸들러입니다. */
  uploadImage: ImageUploader;
  /** 게시글에 첨부할 단일 영상 업로드 필드를 렌더링하는 함수입니다. */
  renderVideoField: (field: {
    value: string | null;
    onChange: (videoId: string | null) => void;
  }) => ReactNode;
}

/**
 * 에디터 하단 저장 버튼의 동작 유형입니다. (01-rules, 흐름 3~5)
 * 일반 저장(SAVE)은 현재 상태를 유지하며, 발행(PUBLISH)과 재공개(REPUBLISH)는 저장 후 상태를 전환합니다.
 */
type SaveAction = 'SAVE' | 'PUBLISH' | 'REPUBLISH';

/** 카테고리를 지정하지 않은("분류 없음") 상태를 나타내는 선택 값입니다. */
const NO_CATEGORY = 'none';

export function PostEditorClient({
  blogSlug,
  categories,
  initialData,
  uploadImage,
  renderVideoField,
}: PostEditorClientProps) {
  const router = useRouter();
  const editorRef = useRef<MDXEditorMethods>(null);

  const titleRef = useRef<HTMLInputElement>(null);
  const [selectedCategory, setSelectedCategory] = useState<string>(
    initialData?.categoryId ?? NO_CATEGORY
  );
  const [videoId, setVideoId] = useState<string | null>(initialData?.videoId ?? null);
  const status = initialData?.status ?? 'DRAFT';
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');
  // 현재 실행 중인 저장 동작을 추적하여 해당 버튼에만 로딩 표시기를 렌더링합니다.
  const [pendingAction, setPendingAction] = useState<SaveAction | null>(null);

  const markdown =
    initialData?.body ||
    `
여기에 **마크다운** 문법을 사용하여 글을 작성할 수 있습니다.

- 리스트 아이템 1
- 리스트 아이템 2
- 리스트 아이템 3

> 인용문 예시입니다.

[MDXEditor 공식 문서](https://mdxeditor.com)

\`\`\`js
// 코드 블록 예시
function helloWorld() {
  console.log("Hello, world!");
}
\`\`\`

### 다음은 테이블 예시입니다.

| 이름  | 나이 | 성별 |
| --- | -- | -- |
| OOO | 20 | 남  |
| OOO | 50 | 여  |


\n\n
해당 내용은 샘플 마크다운 내용입니다. 당신의 이야기로 수정해보세요!
`;

  const handleEditorAreaClick = (e: React.MouseEvent) => {
    const target = e.target as HTMLElement;

    // 툴바 영역 클릭은 무시합니다.
    if (target.closest('.mdxeditor-toolbar')) {
      return;
    }

    // 본문 편집 영역 내부 클릭은 무시합니다.
    if (
      target.getAttribute('contenteditable') === 'true' ||
      target.closest('[contenteditable="true"]')
    ) {
      return;
    }

    editorRef.current?.focus();

    // 포커스 이동이 끝난 뒤 본문 하단 여백을 클릭한 경우 커서를 문서 끝으로 옮깁니다.
    setTimeout(() => {
      const editorContainer = target.closest('.mdxeditor') as HTMLElement;
      const editableEl = editorContainer?.querySelector('[contenteditable="true"]') as HTMLElement;

      if (editableEl) {
        const clickY = e.clientY;

        const allElements = Array.from(editableEl.querySelectorAll('*'));
        const lastVisibleElement = allElements
          .reverse()
          .find((el) => el.textContent?.trim() && el.getBoundingClientRect().height > 0);

        if (lastVisibleElement) {
          const lastRect = lastVisibleElement.getBoundingClientRect();

          // 마지막 본문 요소 아래 10px 이상의 여백을 클릭했을 때 커서를 끝으로 배치합니다.
          if (clickY > lastRect.bottom + 10) {
            const range = document.createRange();
            const sel = window.getSelection();
            range.selectNodeContents(editableEl);
            range.collapse(false);
            sel?.removeAllRanges();
            sel?.addRange(range);
          }
        }
      }
    }, 10);
  };

  const handleSave = useCallback(
    async (action: SaveAction) => {
      const title = titleRef.current?.value ?? '';
      const body = editorRef.current?.getMarkdown() || '';
      if (!title.trim() || !body.trim()) {
        alert('제목과 내용을 입력해주세요.');
        return;
      }

      setSaveStatus('saving');
      setPendingAction(action);
      const request: PostRequest = {
        title,
        body,
        // 작성자가 직접 입력한 요약이 있으면 유지하고, 없으면 서버가 본문에서 자동 생성합니다.
        summary: initialData?.summaryWrittenByAuthor ? initialData.summary : null,
        categoryId: selectedCategory === NO_CATEGORY ? null : selectedCategory,
        videoId,
      };

      try {
        const saved = initialData
          ? await revisePostAction(initialData.postId, request)
          : await writePostAction(blogSlug, request);
        if (!saved.success) throw saved.error;

        const statusChange =
          action === 'PUBLISH'
            ? await publishPostAction(saved.data.postId)
            : action === 'REPUBLISH'
              ? await republishPostAction(saved.data.postId)
              : null;
        if (statusChange && !statusChange.success) throw statusChange.error;

        setSaveStatus('saved');
        alert(
          action === 'SAVE'
            ? status === 'DRAFT'
              ? '포스트가 임시 저장되었습니다.'
              : '포스트가 저장되었습니다.'
            : '포스트가 성공적으로 게시되었습니다.'
        );
        router.replace(PATHS.DASHBOARD);
      } catch (error) {
        setSaveStatus('error');
        alert(
          publishingErrorMessage(error as { code: string }) ??
            '포스트 저장에 실패했습니다. 다시 시도해주세요.'
        );
      } finally {
        setPendingAction(null);
        setTimeout(() => setSaveStatus('idle'), 2000);
      }
    },
    [selectedCategory, blogSlug, router, initialData, videoId, status]
  );

  const handleExit = () => {
    if (confirm('작성 중인 내용이 저장되지 않았을 수 있습니다. 정말 나가시겠습니까?')) {
      router.back();
    }
  };

  return (
    <div className="mx-auto flex w-full max-w-6xl flex-1 flex-col p-4 md:p-8">
      <div className="mb-4 flex items-center space-x-4">
        <Select value={selectedCategory} onValueChange={setSelectedCategory}>
          <SelectTrigger className="w-full md:w-60" aria-label="카테고리 선택">
            <SelectValue placeholder="카테고리 선택" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={NO_CATEGORY}>분류 없음</SelectItem>
            {categories.map((category) => (
              <SelectItem key={category.id} value={category.id}>
                {category.parentId ? '└ ' : ''}
                {category.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {renderVideoField({ value: videoId, onChange: setVideoId })}

      {/* 키 입력마다 MDXEditor 전체가 리렌더링되어 입력이 지연되는 것을 막기 위해 비제어(ref) 입력을 사용합니다. */}
      <div className="mb-4">
        <Input
          ref={titleRef}
          type="text"
          defaultValue={initialData?.title || ''}
          placeholder="제목을 입력하세요"
          className="h-auto bg-white px-4 py-3 text-2xl font-semibold md:text-2xl"
        />
      </div>

      <div
        className="flex flex-1 cursor-text flex-col rounded-md border bg-white [&_.mdxeditor-toolbar]:cursor-default"
        onClick={handleEditorAreaClick}
      >
        <ForwardRefEditor
          ref={editorRef}
          markdown={markdown}
          spellCheck={false}
          translation={mdxEditorKoTranslation}
          className="prose max-w-none flex-1"
          plugins={[
            headingsPlugin({ allowedHeadingLevels: [1, 2, 3] }),
            quotePlugin(),
            listsPlugin(),
            thematicBreakPlugin(),
            linkPlugin(),
            linkDialogPlugin(),
            tablePlugin(),
            imagePlugin({
              imageUploadHandler: async (image: File) => {
                return await uploadImage(image);
              },
              ImageDialog: CustomImageDialog,
            }),
            codeBlockPlugin({
              defaultCodeBlockLanguage: 'js',
              codeBlockEditorDescriptors: [
                { priority: -10, match: (_) => true, Editor: CodeMirrorEditor },
              ],
            }),
            codeMirrorPlugin({
              codeBlockLanguages: {
                js: 'JavaScript',
                ts: 'TypeScript',
                html: 'HTML',
                css: 'CSS',
                java: 'Java',
                kotlin: 'Kotlin',
                bash: 'Bash',
              },
              // 기본 라이트 테마의 낮은 대비를 보완하기 위해 커스텀 구문 강조 스타일을 적용합니다.
              codeMirrorExtensions: [editorSyntaxHighlighting],
            }),
            diffSourcePlugin({ diffMarkdown: 'An older version', viewMode: 'rich-text' }),
            toolbarPlugin({
              toolbarContents: () => (
                <DiffSourceToggleWrapper>
                  <ConditionalContents
                    options={[
                      {
                        when: (editor) => editor?.editorType === 'codeblock',
                        contents: () => <ChangeCodeMirrorLanguage />,
                      },
                      {
                        fallback: () => (
                          <>
                            <UndoRedo />
                            <Separator />
                            <BoldItalicUnderlineToggles />
                            <CodeToggle />
                            <HighlightToggle />
                            <Separator />
                            <StrikeThroughSupSubToggles />
                            <Separator />
                            <ListsToggle />
                            <Separator />

                            <ConditionalContents
                              options={[
                                {
                                  when: whenInAdmonition,
                                  contents: () => <ChangeAdmonitionType />,
                                },
                                { fallback: () => <BlockTypeSelect /> },
                              ]}
                            />

                            <Separator />
                            <CreateLink />
                            <CustomInsertImage upload={uploadImage} />
                            <Separator />
                            <InsertTable />
                            <InsertThematicBreak />
                            <Separator />
                            <InsertCodeBlock />
                            <ConditionalContents
                              options={[
                                {
                                  when: (editorInFocus) => !whenInAdmonition(editorInFocus),
                                  contents: () => (
                                    <>
                                      <Separator />
                                      <InsertAdmonition />
                                    </>
                                  ),
                                },
                              ]}
                            />
                          </>
                        ),
                      },
                    ]}
                  />
                </DiffSourceToggleWrapper>
              ),
            }),
            directivesPlugin({ directiveDescriptors: [AdmonitionDirectiveDescriptor] }),
            // 앞서 등록된 플러그인들을 기준으로 마크다운 단축키 변환기를 구성하도록 마지막에 배치합니다.
            markdownShortcutPlugin(),
            codeFenceOnEnterPlugin(),
          ]}
        />
      </div>

      <div className="mt-6 flex items-center justify-between">
        <Button variant="ghost" onClick={handleExit} className="group text-gray-500">
          <ArrowLeft className="h-4 w-4 transition-transform group-hover:-translate-x-1" />
          <span className="font-semibold">나가기</span>
        </Button>

        <div className="flex items-center justify-end space-x-3">
          <Button
            variant={status === 'PUBLISHED' ? 'default' : 'secondary'}
            onClick={() => handleSave('SAVE')}
            disabled={saveStatus === 'saving'}
          >
            {pendingAction === 'SAVE' && <Loader2 className="h-4 w-4 animate-spin" />}
            {pendingAction === 'SAVE' ? '저장 중...' : status === 'DRAFT' ? '임시 저장' : '저장'}
          </Button>

          {status === 'DRAFT' && (
            <Button onClick={() => handleSave('PUBLISH')} disabled={saveStatus === 'saving'}>
              {pendingAction === 'PUBLISH' && <Loader2 className="h-4 w-4 animate-spin" />}
              {pendingAction === 'PUBLISH' ? '출간 중...' : '출간하기'}
            </Button>
          )}

          {status === 'PRIVATE' && (
            <Button onClick={() => handleSave('REPUBLISH')} disabled={saveStatus === 'saving'}>
              {pendingAction === 'REPUBLISH' && <Loader2 className="h-4 w-4 animate-spin" />}
              {pendingAction === 'REPUBLISH' ? '공개 중...' : '저장하고 다시 공개'}
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
