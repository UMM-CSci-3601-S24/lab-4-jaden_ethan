export interface Todo {
  _id: string;
  owner: string;
  status: boolean;
  body: string;
  category: string;
  limit?: number;
}
export type TodoCategory = 'groceries' | 'homework' | 'software design' | 'video games';