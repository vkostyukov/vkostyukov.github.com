case class Todo(id: String, title: String, completed: Boolean, order: Int)

object Todo {
  private [this] val db: Map[String, Todo] = ???
  def save(t: Todo): Todo = ???
  def update(t: Todo): Todo = ???
  def list(): List[Todo] = ???
}

val todo: RequestReader[Todo] = body.as[String => Todo]
  .map(UUID)
  .should("has title")(_.title != "")
  .should("has positive order")(_.order > 0)

val getTodos: Router[List[Todo]] = get("todos") {
  Todo.list()
}

val postTodo: Router[Response] = post("todos" ? todo) { t: Todo =>
  val id: String = UUID()
  Created.withHeaders("Location" -> id)(Todo.save(t(id)))
}

val patchTodo: Router[Todo] = patch("todos" / string ? body.as[String => Todo]) { (id: String, t: Todo) =>
  Todo.update(t(id))
}

val api: Service[Request, Response] = (getTodos :+: postTodo :+: patchTodo).toService

