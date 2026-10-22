// The interaction context's API (docs/contexts/interaction/02-implementation.md).

/** Counts for whatever ids were asked; an id with none is 0 or missing. */
export interface InteractionCounts {
  likes: Record<string, number>;
  comments: Record<string, number>;
  subscribers: Record<string, number>;
}

export interface SubscribedBlog {
  blogId: string;
  slug: string;
  subscribedAt: string;
}

/** A comment as readers see it. A removed one keeps its place for its replies, without words or author. */
export interface CommentView {
  commentId: string;
  parentId: string | null;
  commenterId: string | null;
  body: string;
  edited: boolean;
  removed: boolean;
  createdAt: string;
  replies: CommentView[];
}

/** Top-level comments, oldest first, 20 a page, each with all its replies. */
export interface CommentPage {
  comments: CommentView[];
  nextCursor: string | null;
}

/** Who wrote a comment, as the page put it together from member's public profiles. */
export interface CommentAuthor {
  displayName: string;
  pictureUrl: string | null;
}
