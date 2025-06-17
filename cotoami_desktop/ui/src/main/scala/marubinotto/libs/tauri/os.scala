package marubinotto.libs.tauri

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

/** Provides operating system-related utility methods and properties.
  */
@js.native
@JSImport("@tauri-apps/plugin-os", JSImport.Namespace)
object os extends js.Object {

  /** Returns the current operating system type. Returns 'linux' on Linux,
    * 'macos' on macOS, 'windows' on Windows, 'ios' on iOS and 'android' on
    * Android.
    */
  def `type`(): String = js.native

  /** Returns the current operating system family. Possible values are 'unix',
    * 'windows'.
    */
  def family(): String = js.native

  /** Returns the current operating system version.
    */
  def version(): String = js.native

  /** Returns the current operating system architecture. Possible values are
    * 'x86', 'x86_64', 'arm', 'aarch64', 'mips', 'mips64', 'powerpc',
    * 'powerpc64', 'riscv64', 's390x', 'sparc64'.
    */
  def arch(): String = js.native
}
