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
  ShowSandpackInfo,
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
  /** The post being edited; none for a new one. */
  initialData?: PostView;
  /**
   * Media's parts, handed in by the page that puts the editor together: an image upload for the
   * body, and the field that uploads the post's one video and gives back its id.
   */
  uploadImage: ImageUploader;
  renderVideoField: (field: {
    value: string | null;
    onChange: (videoId: string | null) => void;
  }) => ReactNode;
}

/**
 * What the save buttons do (publishing 01-rules 흐름 3~5): saving keeps a post's status, so a new
 * post or a draft is saved as a draft and published as a second step; a published post stays
 * published; a private one can be put back out.
 */
type SaveAction = 'SAVE' | 'PUBLISH' | 'REPUBLISH';

/** No category: the post sits under "분류 없음". */
const NO_CATEGORY = 'none';

export function PostEditorClient({
  blogSlug,
  categories,
  initialData,
  uploadImage,
  renderVideoField,
}: PostEditorClientProps) {
  const router = useRouter();
  const editorRef = useRef<MDXEditorMethods>(null); // MDXEditor의 인스턴스에 접근하기 위한 ref 생성

  const titleRef = useRef<HTMLInputElement>(null);
  const [selectedCategory, setSelectedCategory] = useState<string>(
    initialData?.categoryId ?? NO_CATEGORY
  );
  const [videoId, setVideoId] = useState<string | null>(initialData?.videoId ?? null);
  const status = initialData?.status ?? 'DRAFT';
  const [saveStatus, setSaveStatus] = useState<'idle' | 'saving' | 'saved' | 'error'>('idle');
  // 어느 저장 버튼이 진행 중인지 (해당 버튼에만 스피너 표시)
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

    // 툴바 클릭은 무시
    if (target.closest('.mdxeditor-toolbar')) {
      return;
    }

    // contenteditable 내부 클릭은 무시
    if (
      target.getAttribute('contenteditable') === 'true' ||
      target.closest('[contenteditable="true"]')
    ) {
      return;
    }

    editorRef.current?.focus();

    // 약간의 지연 후 커서 위치 조정 (focus가 완료된 후)
    setTimeout(() => {
      const editorContainer = target.closest('.mdxeditor') as HTMLElement;
      const editableEl = editorContainer?.querySelector('[contenteditable="true"]') as HTMLElement;

      if (editableEl) {
        const clickY = e.clientY;

        // 실제 콘텐츠의 마지막 위치 찾기
        const allElements = Array.from(editableEl.querySelectorAll('*'));
        const lastVisibleElement = allElements
          .reverse()
          .find((el) => el.textContent?.trim() && el.getBoundingClientRect().height > 0);

        if (lastVisibleElement) {
          const lastRect = lastVisibleElement.getBoundingClientRect();

          // 콘텐츠 아래 영역 클릭 시 커서를 끝으로
          if (clickY > lastRect.bottom + 10) {
            // 10px 여유
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
      setPendingAction(action); // 진행 중인 버튼에만 스피너를 띄우기 위한 추적
      const request: PostRequest = {
        title,
        body,
        // Revising keeps a summary the author wrote; otherwise the API derives one from the body.
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

      {/* 박스형 타이틀(a안): 고정 툴바가 타이틀-본문 사이를 끊으므로
          문서형(borderless) 대신 필드형을 유지한다 — 포커스 링은 테마(--ring) 적용.
          uncontrolled(ref): controlled로 두면 키 입력마다 MDXEditor를 포함한
          트리 전체가 리렌더되어 타이핑이 밀린다. 값은 저장 시에만 읽는다. */}
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
            // the default code block language to insert when user clicks the "insert code block" button
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
              // 기본 라이트 하이라이트는 대비가 낮아 GitHub Light 계열 팔레트로 교체
              codeMirrorExtensions: [editorSyntaxHighlighting],
            }),
            // the viewMode parameter lets you switch the editor to diff or source mode.
            // you can get the diffMarkdown from your backend and pass it here.
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
                        when: (editor) => editor?.editorType === 'sandpack',
                        contents: () => <ShowSandpackInfo />,
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
            // 모든 플러그인이 등록된 뒤에 와야 활성 플러그인 기준으로 변환기가 구성된다
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
