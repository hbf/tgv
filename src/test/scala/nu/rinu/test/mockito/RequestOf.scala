// Taken from https://github.com/rinunu/mock-http-server/tree/2d8ae2382b1b6f9c40332ff717b6217d519a5038
package nu.rinu.test.mockito

import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.mockito.Matchers.argThat

import nu.rinu.test.Request

/**
 * hamcrest 用の Matcher
 *
 * Mockito etc. とともに使用できる。
 *
 * @param params 指定したパラメータがすべて、リクエストに含まれているならマッチする
 * @param headers 指定したヘッダーがすべて、リクエストに含まれているならマッチする
 */
class RequestOf(url: String, params: Set[(String, String)], headers: Set[(String, String)]) extends BaseMatcher[Request] {

  def matches(a: Any) = {
    val request = a.asInstanceOf[Request]

    request != null &&
      request.url == url &&
      matchesParams(request) &&
      matchesHeaders(request)
  }

  def describeTo(description: Description) {
    description.appendText("url=" + url + ",")
    if (!params.isEmpty) {
      description.appendText("params=" + params + ",")
    }
    if (!headers.isEmpty) {
      description.appendText("headers=" + headers + ",")
    }
  }

  private def matchesParams(request: Request) = matches(params, request.params)

  private def matchesHeaders(request: Request) = matches(headers, request.headers)

  private def matches(exptected: Set[(String, String)], actual: Map[String, Seq[String]]) = {
    //println("exptected: " + exptected)
    //println("actual: " + actual)
    if (exptected.isEmpty) {
      true
    } else {
      exptected.forall(kv =>
        actual.getOrElse(kv._1, Seq()).contains(kv._2))
    }
  }
}

object RequestOf {
  def requestOf(url: String, params: Set[(String, String)] = Set(), headers: Set[(String, String)] = Set()) =
    argThat(new RequestOf(url, params, headers))
}
