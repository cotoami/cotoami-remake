package marubinotto.libs

import scala.scalajs.js
import scala.scalajs.js.|

package object unified {

  // https://github.com/unifiedjs/unified/blob/main/readme.md#processor
  @js.native
  trait Processor extends js.Object {
    def use(
        plugin: js.Any,
        options: js.Object = js.Dynamic.literal()
    ): Processor = js.native

    def process(file: String | VFile): js.Promise[VFile] = js.native

    def processSync(file: String | VFile): VFile = js.native
  }

// https://github.com/vfile/vfile
  @js.native
  trait VFile extends js.Object {
    val value: String = js.native

    def toString(encoding: String = "utf8"): String = js.native
  }
}
