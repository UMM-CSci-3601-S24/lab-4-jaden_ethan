package umm3601.todos;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import io.javalin.Javalin;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.json.JavalinJackson;
import io.javalin.validation.BodyValidator;
import io.javalin.validation.ValidationException;

/**
 * Tests the logic of the TodoController
 *
 * @throws IOException
 */
// The tests here include a ton of "magic numbers" (numeric constants).
// It wasn't clear to me that giving all of them names would actually
// help things. The fact that it wasn't obvious what to call some
// of them says a lot. Maybe what this ultimately means is that
// these tests can/should be restructured so the constants (there are
// also a lot of "magic strings" that Checkstyle doesn't actually
// flag as a problem) make more sense.
@SuppressWarnings({ "MagicNumber" })
class TodoControllerSpec {

  // An instance of the controller we're testing that is prepared in
  // `setupEach()`, and then exercised in the various tests below.
  private TodoController todoController;

  // A Mongo object ID that is initialized in `setupEach()` and used
  // in a few of the tests. It isn't used all that often, though,
  // which suggests that maybe we should extract the tests that
  // care about it into their own spec file?
  private ObjectId frysId;

  // The client and database that will be used
  // for all the tests in this spec file.
  private static MongoClient mongoClient;
  private static MongoDatabase db;

  // Used to translate between JSON and POJOs.
  private static JavalinJackson javalinJackson = new JavalinJackson();

  @Mock
  private Context ctx;

  @Captor
  private ArgumentCaptor<ArrayList<Todo>> todoArrayListCaptor;

  @Captor
  private ArgumentCaptor<Todo> todoCaptor;

  @Captor
  private ArgumentCaptor<Map<String, String>> mapCaptor;

  /**
   * Sets up (the connection to the) DB once; that connection and DB will
   * then be (re)used for all the tests, and closed in the `teardown()`
   * method. It's somewhat expensive to establish a connection to the
   * database, and there are usually limits to how many connections
   * a database will support at once. Limiting ourselves to a single
   * connection that will be shared across all the tests in this spec
   * file helps both speed things up and reduce the load on the DB
   * engine.
   */
  @BeforeAll
  static void setupAll() {
    String mongoAddr = System.getenv().getOrDefault("MONGO_ADDR", "localhost");

    mongoClient = MongoClients.create(
        MongoClientSettings.builder()
            .applyToClusterSettings(builder -> builder.hosts(Arrays.asList(new ServerAddress(mongoAddr))))
            .build());
    db = mongoClient.getDatabase("test");
  }

  @AfterAll
  static void teardown() {
    db.drop();
    mongoClient.close();
  }

  @BeforeEach
  void setupEach() throws IOException {
    // Reset our mock context and argument captor (declared with Mockito annotations
    // @Mock and @Captor)
    MockitoAnnotations.openMocks(this);

    // Setup database
    MongoCollection<Document> todoDocuments = db.getCollection("todos");
    todoDocuments.drop();
    List<Document> testTodos = new ArrayList<>();
    testTodos.add(
        new Document()
            .append("owner", "Jerry")
            .append("status", true)
            .append("body", "Prominent skier")
            .append("category", "groceries"));
    testTodos.add(
        new Document()
            .append("owner", "Swan")
            .append("status", false)
            .append("body", "crazy")
            .append("category", "software design"));
    testTodos.add(
        new Document()
            .append("owner", "Rod")
            .append("status", true)
            .append("body", "Hunter")
            .append("category", "video games"));

    frysId = new ObjectId();
    Document fry = new Document()
        .append("_id", frysId)
        .append("owner", "Fry")
        .append("status", true)
        .append("body", "Bullfrog ranger")
        .append("category", "homework");

    todoDocuments.insertMany(testTodos);
    todoDocuments.insertOne(fry);

    todoController = new TodoController(db);
  }

  /**
   * Verify that we can successfully build a TodoController
   * and call it's `addRoutes` method. This doesn't verify
   * much beyond that the code actually runs without throwing
   * an exception. We do, however, confirm that the `addRoutes`
   * causes `.get()` to be called at least twice.
   */
  @Test
  public void canBuildController() throws IOException {
    Javalin mockServer = Mockito.mock(Javalin.class);
    todoController.addRoutes(mockServer);

    // Verify that calling `addRoutes()` above caused `get()` to be called
    // on the server at least twice. We use `any()` to say we don't care about
    // the arguments that were passed to `.get()`.
    verify(mockServer, Mockito.atLeast(2)).get(any(), any());
  }

  @Test
  void canGetAllTodos() throws IOException {
    // When something asks the (mocked) context for the queryParamMap,
    // it will return an empty map (since there are no query params in this case
    // where we want all todos)
    when(ctx.queryParamMap()).thenReturn(Collections.emptyMap());

    // Now, go ahead and ask the todoControlle to getTodos
    // (which will, indeed, ask the context for its queryParamMap)
    todoController.getTodos(ctx);

    // We are going to capture an argument to a function, and the type of that
    // argument will be
    // of type ArrayList<Todo> (we said so earlier using a Mockito annotation like
    // this):
    // @Captor
    // private ArgumentCaptor<ArrayList<Todo>> todoArrayListCaptor;
    // We only want to declare that captor once and let the annotation
    // help us accomplish reassignment of the value for the captor
    // We reset the values of our annotated declarations using the command
    // `MockitoAnnotations.openMocks(this);` in our @BeforeEach

    // Specifically, we want to pay attention to the ArrayList<Todo> that is passed
    // as input
    // when ctx.json is called --- what is the argument that was passed? We capture
    // it and can refer to it later
    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Check that the database collection holds the same number of documents as the
    // size of the captured List<Todo>
    assertEquals(db.getCollection("todos").countDocuments(), todoArrayListCaptor.getValue().size());
  }

  // @Test
  // void canGetTodosWithOwnerJerry() throws IOException {
  //   // Add a query param map to the context that maps "owner" to "Jerry".
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {"Jerry"}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class))
  //       .thenReturn(Validator.create(String.class, "Jerry", TodoController.OWNER_KEY));

  //   todoController.getTodos(ctx);

  //   verify(ctx).json(todoArrayListCaptor.capture());
  //   verify(ctx).status(HttpStatus.OK);
  //   assertEquals(2, todoArrayListCaptor.getValue().size());
  //   for (Todo todo : todoArrayListCaptor.getValue()) {
  //     assertEquals("Jerry", todo.owner);
  //   }
  // }

  // // We've included another approach for testing if everything behaves when we ask
  // // for todos that are Jerry
  // @Test
  // void canGetTodosWithOwnerJerryRedux() throws JsonMappingException, JsonProcessingException {
  //   // When the controller calls `ctx.queryParamMap`, return the expected map for an
  //   // "?owner=Jerry" query.
  //   when(ctx.queryParamMap()).thenReturn(Map.of(TodoController.OWNER_KEY, List.of("Jerry")));
  //   // When the controller calls `ctx.queryParamAsClass() to get the value
  //   // associated with
  //   // the "owner" key, return an appropriate Validator.
  //   Validator<Integer> validator = Validator.create(Integer.class, "Jerry", TodoController.OWNER_KEY);
  //   when(ctx.queryParamAsClass(TodoController.OWNER_KEY, Integer.class)).thenReturn(validator);

  //   // Call the method under test.
  //   todoController.getTodos(ctx);

  //   // Verify that `getTodos` included a call to `ctx.status(HttpStatus.OK)` at some
  //   // point.
  //   verify(ctx).status(HttpStatus.OK);

  //   // Instead of using the Captor like in many other tests, we will use an
  //   // ArgumentMatcher
  //   // Verify that `ctx.json()` is called with a `List` of `Todo`s.
  //   // Each of those `Todo`s should have owner Jerry.
  //   verify(ctx).json(argThat(new ArgumentMatcher<List<Todo>>() {
  //     @Override
  //     public boolean matches(List<Todo> todos) {
  //       for (Todo todo : todos) {
  //         assertEquals("Jerry", todo.owner);
  //       }
  //       return true;
  //     }
  //   }));
  // }

  /**
   * Test that if the todo sends a request with an illegal value in
   * the owner field (i.e., something that can't be parsed to a number)
   * we get a reasonable error code back.
   */
  // @Test
  // void respondsAppropriatelyToNonStringOwner() {
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {"9000"}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   when(ctx.queryParamAsClass(TodoController.OWNER_KEY, String.class))
  //       .thenReturn(Validator.create(String.class, "bad", TodoController.OWNER_KEY));

  //   // This should now throw a `ValidationException` because
  //   // our request has an owner that can't be parsed to a number,
  //   // but I don't yet know how to make the messowner be anything specific
  //   assertThrows(ValidationException.class, () -> {
  //     todoController.getTodos(ctx);
  //   });
  // }

  // /**
  //  * Test that if the todo sends a request with an illegal value in
  //  * the owner field (i.e., too big of a number)
  //  * we get a reasonable error code back.
  //  */
  // @Test
  // void respondsAppropriatelyToTooLargeBody() {
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   queryParams.put(TodoController.BODY_KEY, Arrays.asList(new String[] {"JERRY"}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   when(ctx.queryParamAsClass(TodoController.OWNER_KEY, Integer.class))
  //       .thenReturn(Validator.create(Integer.class, "JERRY", TodoController.BODY_KEY));

  //   // This should now throw a `ValidationException` because
  //   // our request has an owner that is larger than 150, which isn't allowed,
  //   // but I don't yet know how to make the messowner be anything specific
  //   assertThrows(ValidationException.class, () -> {
  //     todoController.getTodos(ctx);
  //   });
  // }

  @Test
  void canGetTodosWithCategory() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {"video games"}));
    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(new String[] {"Complete"}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn("video games");
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn("Complete");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the todos passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals("Runners", todo.category);
    }
  }

  @Test
  void canGetTodosWithOwner() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {"Jerry"}));
    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(new String[] {"Complete"}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.OWNER_KEY)).thenReturn("Jerry");
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn("Complete");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the todos passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals("Jerry", todo.owner);
    }
  }

  @Test
  void canGetTodosWithBody() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.BODY_KEY, Arrays.asList(new String[] {"Prominent skier"}));
    queryParams.put(TodoController.STATUS_KEY, Arrays.asList(new String[] {"Complete"}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.BODY_KEY)).thenReturn("Prominent skier");
    when(ctx.queryParam(TodoController.STATUS_KEY)).thenReturn("Complete");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the todos passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals("Prominent skier", todo.body);
    }
  }

  @Test
  void canGetTodosWithCategoryLowercase() throws IOException {
    Map<String, List<String>> queryParams = new HashMap<>();
    queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {"homework"}));
    when(ctx.queryParamMap()).thenReturn(queryParams);
    when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn("homework");

    todoController.getTodos(ctx);

    verify(ctx).json(todoArrayListCaptor.capture());
    verify(ctx).status(HttpStatus.OK);

    // Confirm that all the todos passed to `json` work for OHMNET.
    for (Todo todo : todoArrayListCaptor.getValue()) {
      assertEquals("homework", todo.category);
    }
  }

  // @Test
  // void getTodosByBody() throws IOException {
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {"Hunter"}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   when(ctx.queryParamAsClass(TodoController.CATEGORY_KEY, String.class))
  //       .thenReturn(Validator.create(String.class, "Hunter", TodoController.BODY_KEY));

  //   todoController.getTodos(ctx);

  //   verify(ctx).json(todoArrayListCaptor.capture());
  //   verify(ctx).status(HttpStatus.OK);
  //   assertEquals(2, todoArrayListCaptor.getValue().size());
  // }

  // @Test
  // void getTodosByOwnerAndCategory() throws IOException {
  //   Map<String, List<String>> queryParams = new HashMap<>();
  //   queryParams.put(TodoController.CATEGORY_KEY, Arrays.asList(new String[] {"Runners"}));
  //   queryParams.put(TodoController.OWNER_KEY, Arrays.asList(new String[] {"Jerry"}));
  //   when(ctx.queryParamMap()).thenReturn(queryParams);
  //   when(ctx.queryParam(TodoController.CATEGORY_KEY)).thenReturn("Runners");
  //   when(ctx.queryParamAsClass(TodoController.OWNER_KEY, Integer.class))
  //       .thenReturn(Validator.create(Integer.class, "Jerry", TodoController.OWNER_KEY));

  //   todoController.getTodos(ctx);

  //   verify(ctx).json(todoArrayListCaptor.capture());
  //   verify(ctx).status(HttpStatus.OK);
  //   assertEquals(1, todoArrayListCaptor.getValue().size());
  //   for (Todo todo : todoArrayListCaptor.getValue()) {
  //     assertEquals("Runners", todo.category);
  //     assertEquals("Jerry", todo.owner);
  //   }
  // }

  @Test
  void getTodoWithExistentId() throws IOException {
    String id = frysId.toHexString();
    when(ctx.pathParam("id")).thenReturn(id);

    todoController.getTodo(ctx);

    verify(ctx).json(todoCaptor.capture());
    verify(ctx).status(HttpStatus.OK);
    assertEquals("Fry", todoCaptor.getValue().owner);
    assertEquals(frysId.toHexString(), todoCaptor.getValue()._id);
  }

  @Test
  void getTodoWithBadId() throws IOException {
    when(ctx.pathParam("_id")).thenReturn("bad");

    Throwable exception = assertThrows(BadRequestResponse.class, () -> {
      todoController.getTodo(ctx);
    });

    assertEquals("The requested todo id wasn't a legal Mongo Object ID.", exception.getMessage());
  }

  @Test
  void getTodoWithNonexistentId() throws IOException {
    String id = "588935f5c668650dc77df581";
    when(ctx.pathParam("id")).thenReturn(id);

    Throwable exception = assertThrows(NotFoundResponse.class, () -> {
      todoController.getTodo(ctx);
    });

    assertEquals("The requested todo was not found", exception.getMessage());
  }

  // @Captor
  // private ArgumentCaptor<ArrayList<TodoByOwner>> todoByOwnerListCaptor;

  // @Test
  // public void testGetTodosGroupedByCategory() {
  //   when(ctx.queryParam("sortBy")).thenReturn("category");
  //   when(ctx.queryParam("sortOrder")).thenReturn("asc");
  //   todoController.getTodosGroupedByCompany(ctx);

  //  // Capture the argument to `ctx.json()`
  //   verify(ctx).json(todoByOwnerListCaptor.capture());

  //   // Get the value that was passed to `ctx.json()`
  //   ArrayList<TodoByOwner> result = todoByOwnerListCaptor.getValue();

  //   // There are 3 companies in the test data, so we should have 3 entries in the
  //   // result.
  //   assertEquals(3, result.size());

  //   // The companies should be in alphabetical order by company name,
  //   // and with todo counts of 1, 2, and 1, respectively.
  //   TodoByOwner jerry = result.get(0);
  //   assertEquals("Jerry", jerry._id);
  //   assertEquals(1, jerry.count);
  //   TodoByOwner Rod = result.get(1);
  //   assertEquals("Rod", Rod._id);
  //   assertEquals(2, Rod.count);
  //   TodoByOwner Swan = result.get(2);
  //   assertEquals("Swan", Swan._id);
  //   assertEquals(1, Swan.count);
  // }

  // @Test
  // public void testGetTodosGroupedByOwnerOrderedByCount() {
  //   when(ctx.queryParam("sortBy")).thenReturn("count");
  //   when(ctx.queryParam("sortOrder")).thenReturn("asc");
  //   todoController.getTodosGroupedByCompany(ctx);

  //   // Capture the argument to `ctx.json()`
  //   verify(ctx).json(todoByOwnerListCaptor.capture());

  //   // Get the value that was passed to `ctx.json()`
  //   ArrayList<TodoByOwner> result = todoByOwnerListCaptor.getValue();

  //   // There are 3 companies in the test data, so we should have 3 entries in the
  //   // result.
  //   assertEquals(1, result.size());

  //   // The companies should be in order by todo count, and with counts of 1, 1, and 2,
  //   // respectively. We don't know which order "IBM" and "UMM" will be in, since they
  //   // both have a count of 1. So we'll get them both and then swap them if necessary.
  //   TodoByOwner jerry = result.get(0);
  //   TodoByOwner Swan = result.get(1);
  //   if (jerry._id.equals("4567")) {
  //     jerry = result.get(0);
  //     Swan = result.get(1);
  //   }
  //   TodoByOwner Rod = result.get(2);
  //   assertEquals("0002", Rod._id);
  //   assertEquals(1, Rod.count);
  //   assertEquals("1234", Swan._id);
  //   assertEquals(1, Swan.count);
  //   assertEquals("4567", jerry._id);
  //   assertEquals(2, jerry.count);
  // }

  @Test
  void addTodo() throws IOException {
    String testNewTodo = """
        {
          "_id": "TestTodo",
          "owner": "Jerry",
          "status": true,
          "body": "cool stuff",
          "category": "homework"
        }
        """;
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(testNewTodo, Todo.class, javalinJackson));

    todoController.addNewTodo(ctx);
    verify(ctx).json(mapCaptor.capture());

    // Our status should be 201, i.e., our new todo was successfully created.
    verify(ctx).status(HttpStatus.CREATED);

    // Verify that the todo was added to the database with the correct ID
    Document addedTodo = db.getCollection("todos")
        .find(eq("_id", new ObjectId(mapCaptor.getValue().get("id")))).first();

    // Successfully adding the todo should return the newly generated, non-empty
    // MongoDB ID for that todo.
    assertNotEquals("not this", addedTodo.get("_id"));
    assertEquals("Jerry", addedTodo.get(TodoController.OWNER_KEY));
    assertEquals("homework", addedTodo.get(TodoController.CATEGORY_KEY));
    assertEquals("cool stuff", addedTodo.get("body"));
    assertEquals(true, addedTodo.get(TodoController.STATUS_KEY));
  }

  @Test
  void addInvalidStatusTodo() throws IOException {
    String testNewTodo = """
        {
          "_id": "TestTodo",
          "owner": "Jerry",
          "status": fake,
          "body": "cool stuff",
          "category": "homework"
        }
        """;
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(testNewTodo, Todo.class, javalinJackson));

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });

    // Our status should be 400, because our request contained information that
    // didn't validate.
    // However, I'm not yet sure how to test the specifics about validation problems
    // encountered.
    // verify(ctx).status(HttpStatus.BAD_REQUEST);
  }


  @Test
  void addInvalidCategoryTodo() throws IOException {
    String testNewTodo = """
        {
          "_id": "TestTodo",
          "owner": "Jerry",
          "status": true,
          "body": "cool stuff",
          "category": "real category"
        }
        """;
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(testNewTodo, Todo.class, javalinJackson));

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  void addNullOwnerTodo() throws IOException {
    String testNewTodo = """
        {
          "_id": "TestTodo",
          "status": true,
          "body": "cool stuff",
          "category": "homework"
        }
        """;
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(testNewTodo, Todo.class, javalinJackson));

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  void addNullBodyTodo() throws IOException {
    String testNewTodo = """
        {
          "_id": "TestTodo",
          "owner": "Jerry",
          "status": true,
          "category": "homework"
        }
        """;
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(testNewTodo, Todo.class, javalinJackson));

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }

  @Test
  void addNullCategoryTodo() throws IOException {
    String testNewTodo = """
        {
          "_id": "TestTodo",
          "owner": "Jerry",
          "status": true,
          "body": "cool stuff",
        }
        """;
    when(ctx.bodyValidator(Todo.class))
        .then(value -> new BodyValidator<Todo>(testNewTodo, Todo.class, javalinJackson));

    assertThrows(ValidationException.class, () -> {
      todoController.addNewTodo(ctx);
    });
  }


  @Test
  void deleteFoundTodo() throws IOException {
    String testID = frysId.toHexString();
    when(ctx.pathParam("id")).thenReturn(testID);

    // Todo exists before deletion
    assertEquals(1, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));

    todoController.deleteTodo(ctx);

    verify(ctx).status(HttpStatus.OK);

    // Todo is no longer in the database
    assertEquals(0, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));
  }

  @Test
  void tryToDeleteNotFoundTodo() throws IOException {
    String testID = frysId.toHexString();
    when(ctx.pathParam("id")).thenReturn(testID);

    todoController.deleteTodo(ctx);
    // Todo is no longer in the database
    assertEquals(0, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));

    assertThrows(NotFoundResponse.class, () -> {
      todoController.deleteTodo(ctx);
    });

    verify(ctx).status(HttpStatus.NOT_FOUND);

    // Todo is still not in the database
    assertEquals(0, db.getCollection("todos").countDocuments(eq("_id", new ObjectId(testID))));
  }

  /**
   * Test that the `generateAvatar` throws a `NoSuchAlgorithmException`
   * if it can't find the `md5` hashing algortihm.
   *
   * To test this code, we need to mock out the `md5()` method so we
   * can control what it returns. In particular, we want `.md5()` to
   * throw a `NoSuchAlgorithmException`, which we can't do without
   * mocking `.md5()` (since the algorithm does actually exist).
   *
   * The use of `Mockito.spy()` essentially allows us to override
   * the `md5()` method, while leaving the rest of the todo controller
   * "as is". This is a nice way to test a method that depends on
   * an internal method that we don't want to test (`md5()` in this case).
   *
   * This code was suggested by GitHub CoPilot.
   *
   * @throws NoSuchAlgorithmException
   */
  // @Test
  // void testGenerateAvatarWithException() throws NoSuchAlgorithmException {
  //   // Arrange
  //   String email = "test@example.com";
  //   TodoController controller = Mockito.spy(todoController);
  //   when(controller.md5(email)).thenThrow(NoSuchAlgorithmException.class);

  //   // Act
  //   String avatar = controller.generateAvatar(email);

  //   // Assert
  //   assertEquals("https://gravatar.com/avatar/?d=mp", avatar);
  // }
}
