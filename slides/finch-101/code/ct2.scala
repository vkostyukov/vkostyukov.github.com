import com.twitter.finagle.{Httpx, Service}
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Future
import com.twitter.io.Buf

object HelloWorld extends App {
  val service: Service[Request, Response] = new Service[Request, Response] {
    def apply(req: Request): Future[Request] = {
      val rep = Response()
      rep.content = Buf.Utf8("Hello, World!")

      Future.value(rep)
    }
  }

  Httpx.server
    .configured(RequestSemaphoreFilter.Param(new AsyncSemaphore(max, 0))) // max concurrent requests
    .configured(TimeoutFilter.Param(10.seconds)) // request timeout
    .configured(Daemonize(true)) // demonize
    .serve(":8081", service)
}
