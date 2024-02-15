import { Component, OnInit, OnDestroy } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subject, takeUntil } from 'rxjs';
import { Todo, TodoRole } from './todo';
import { TodoService } from './todo.service';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { MatListModule } from '@angular/material/list';

import { MatRadioModule } from '@angular/material/radio';
import { MatOptionModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { FormsModule } from '@angular/forms';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatCardModule } from '@angular/material/card';

/**
 * A component that displays a list of todos, either as a grid
 * of cards or as a vertical list.
 *
 * The component supports local filtering by name and/or company,
 * and remote filtering (i.e., filtering by the server) by
 * role and/or age. These choices are fairly arbitrary here,
 * but in "real" projects you want to think about where it
 * makes the most sense to do the filtering.
 */
@Component({
    selector: 'app-todo-list-component',
    templateUrl: 'todo-list.component.html',
    styleUrls: ['./todo-list.component.scss'],
    providers: [],
    standalone: true,
    imports: [MatCardModule, MatFormFieldModule, MatInputModule, FormsModule, MatSelectModule, MatOptionModule, MatRadioModule, MatListModule, RouterLink, MatButtonModule, MatTooltipModule, MatIconModule]
})

export class TodoListComponent implements OnInit, OnDestroy  {
  // These are public so that tests can reference them (.spec.ts)
  public serverFilteredTodos: Todo[];
  public filteredTodos: Todo[];

  public todoName: string;
  public todoAge: number;
  public todoRole: TodoRole;
  public todoCompany: string;
  public viewType: 'card' | 'list' = 'card';

  errMsg = '';
  private ngUnsubscribe = new Subject<void>();


  /**
   * This constructor injects both an instance of `TodoService`
   * and an instance of `MatSnackBar` into this component.
   * `TodoService` lets us interact with the server.
   *
   * @param todoService the `TodoService` used to get todos from the server
   * @param snackBar the `MatSnackBar` used to display feedback
   */
  constructor(private todoService: TodoService, private snackBar: MatSnackBar) {
    // Nothing here – everything is in the injection parameters.
  }

  /**
   * Get the todos from the server, filtered by the role and age specified
   * in the GUI.
   */
  getTodosFromServer(): void {
    // A todo-list-component is paying attention to todoService.getTodos
    // (which is an Observable<Todo[]>)
    // (for more on Observable, see: https://reactivex.io/documentation/observable.html)
    // and we are specifically watching for role and age whenever the Todo[] gets updated
    this.todoService.getTodos({
      role: this.todoRole,
      age: this.todoAge
    }).pipe(
      takeUntil(this.ngUnsubscribe)
    ).subscribe({
      // Next time we see a change in the Observable<Todo[]>,
      // refer to that Todo[] as returnedTodos here and do the steps in the {}
      next: (returnedTodos) => {
        // First, update the array of serverFilteredTodos to be the Todo[] in the observable
        this.serverFilteredTodos = returnedTodos;
        // Then update the filters for our client-side filtering as described in this method
        this.updateFilter();
      },
      // If we observe an error in that Observable, put that message in a snackbar so we can learn more
      error: (err) => {
        if (err.error instanceof ErrorEvent) {
          this.errMsg = `Problem in the client – Error: ${err.error.message}`;
        } else {
          this.errMsg = `Problem contacting the server – Error Code: ${err.status}\nMessage: ${err.message}`;
        }
        this.snackBar.open(
          this.errMsg,
          'OK',
          // The message will disappear after 6 seconds.
          { duration: 6000 });
      },
      // Once the observable has completed successfully
      // complete: () => console.log('Todos were filtered on the server')
    });
  }

  /**
   * Called when the filtering information is changed in the GUI so we can
   * get an updated list of `filteredTodos`.
   */
  public updateFilter(): void {
    this.filteredTodos = this.todoService.filterTodos(
      this.serverFilteredTodos, { name: this.todoName, company: this.todoCompany });
  }

  /**
   * Starts an asynchronous operation to update the todos list
   *
   */
  ngOnInit(): void {
    this.getTodosFromServer();
  }

  /**
   * When this component is destroyed, we should unsubscribe to any
   * outstanding requests.
   */
  ngOnDestroy() {
    this.ngUnsubscribe.next();
    this.ngUnsubscribe.complete();
  }

}
