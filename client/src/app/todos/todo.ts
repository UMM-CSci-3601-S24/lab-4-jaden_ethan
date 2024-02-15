export interface Todo {
    _id: string;
    name: string;
    age: number;
    company: string;
    email: string;
    avatar?: string;
    role: TodoRole;
  }
  
  export type TodoRole = 'admin' | 'editor' | 'viewer';
  