// Taken from https://github.com/rinunu/mock-http-server/tree/2d8ae2382b1b6f9c40332ff717b6217d519a5038
package nu.rinu.test

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.eclipse.jetty.server.{ Request => JRequest }
import scala.collection.JavaConverters._
import org.slf4j.LoggerFactory

class HttpServer(port: Int, var handler: HttpServerHandler = null) {
  val logger = LoggerFactory.getLogger(classOf[HttpServer])

  val impl = new Server(port)
  impl.setStopAtShutdown(true)

  def url = "http://localhost:" + impl.getConnectors()(0).getLocalPort

  private implicit def toScala(from: java.util.Map[String, Array[String]]): Map[String, Seq[String]] = {
    from.asScala.toMap.map(a => (a._1, a._2.toSeq))
  }

  impl.setHandler(new AbstractHandler {
    override def handle(target: String, baseRequest: JRequest, request: HttpServletRequest, response: HttpServletResponse) {
      val (method, f) = request.getMethod match {
        case "GET" => (Method.Get, handler.get _)
        case "POST" => (Method.Post, handler.post _)
      }

      logger.debug("request: " + request.getRequestURI)
      val req = new Request(method, request.getRequestURI, request.getParameterMap, headers = headers(request))
      val res = f(req)

      if (res != null) {
        logger.debug("response: %d".format(res.statusCode))

        response.setCharacterEncoding("UTF-8") // KF: added
        response.getWriter.append(res.body)
        response.setStatus(res.statusCode)
        for (h <- res.headers) {
          response.addHeader(h._1, h._2)
        }

        baseRequest.setHandled(true)
      } else {
        logger.debug("response: 404")
      }
    }
  })
  impl.start
  logger.debug("start: " + url)

  def stop() {
    impl.stop
    impl.join
  }

  private def headers(request: HttpServletRequest): Map[String, Seq[String]] = {
    val tuples = for (name <- request.getHeaderNames.asScala) yield {
      (name,
        for (value <- request.getHeaders(name).asScala.toSeq) yield value)
    }
    tuples.toMap
  }
}

object Method extends Enumeration {
  val Get = Value("get")
  val Post = Value("post")
  val Put = Value("put")
  val Delete = Value("delete")
}

/**
 * 設計
 * HttpServletRequest は verify のタイミングでアクセス出来なかったため、 immutable な独自のオブジェクトとする
 */
case class Request(method: Method.Value, url: String, params: Map[String, Seq[String]] = Map(), headers: Map[String, Seq[String]] = Map()) {
}

case class Response(statusCode: Int = 200, body: String = "", headers: Set[(String, String)] = Set()) {
}

object Response {
  implicit def toResponse(body: String) = Response(200, body)
  implicit def toResponse(statusCode: Int) = Response(statusCode, "dummy")
}

/**
 * HTTP リクエストを実際に処理する
 *
 * モックにすることを想定している
 */
trait HttpServerHandler {
  def get(request: Request): Response
  def post(request: Request): Response
}
