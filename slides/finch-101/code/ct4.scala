import com.twitter.finagle.{Httpx, Service}
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.util.Future
import com.twitter.io.Buf

object HelloWorld extends App {

  val hello: Router[Stirng] = 
    get("hello" / string) { name: String =>
      s"Hello, $name!"
    }

  val greet: Router[String] = 
    get("greet" / string ? paramOption("title")) { (name: String, title: Option[Stirng]) => 
      s"Hello, ${title.getOrElse("") $name}!"
    }

  Httpx.server.serve(":8081", (hello :+: greet).toService)
}
