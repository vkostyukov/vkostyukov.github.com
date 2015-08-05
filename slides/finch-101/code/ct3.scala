import com.twitter.finagle.{Httpx, Service}
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Future
import com.twitter.io.Buf

object HelloWorld extends App {
  val hello: Router[Stirng] = Router.value("Hello, World!")

  Httpx.server.serve(":8081", hello.toService)
}
