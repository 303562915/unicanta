@file:OptIn(ExperimentalSerializationApi::class)

package io.github.samolego.canta.packages

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Outcome of uninstalling or reinstalling one package, streamed by the helper
 * as one message per package.
 *
 * @param status a `PackageInstaller.STATUS_*` code, or [STATUS_NO_RESULT].
 */
@Serializable
data class OperationResult(
    @ProtoNumber(1) val packageName: String = "",
    @ProtoNumber(2) val success: Boolean = false,
    @ProtoNumber(3) val status: Int = 0,
    @ProtoNumber(4) val message: String = "",
) {
    companion object {
        /** No status arrived in time (or the call failed before running). */
        const val STATUS_NO_RESULT = -1000

        /**
         * The device refused outright, without going through the installer: a hidden call that
         * threw, a policy that said no. Distinct from [STATUS_NO_RESULT] because nothing is
         * pending — the answer was simply negative, and [OperationResult.message] says why.
         */
        const val STATUS_NEGATIVE_RESPONSE = -1001
    }
}
