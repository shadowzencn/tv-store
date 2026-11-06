package com.shadowzen.tvstore.core.installer

sealed interface InstallDecision {
    val allowed: Boolean

    data object NewInstall : InstallDecision {
        override val allowed = true
    }

    data class Upgrade(val fromVersionCode: Long, val toVersionCode: Long) : InstallDecision {
        override val allowed = true
    }

    data class Reinstall(val versionCode: Long) : InstallDecision {
        override val allowed = true
    }

    data class RejectedDowngrade(val installedVersionCode: Long, val candidateVersionCode: Long) : InstallDecision {
        override val allowed = false
    }

    data object RejectedSignatureMismatch : InstallDecision {
        override val allowed = false
    }

    data class RejectedPackageMismatch(val expected: String, val actual: String) : InstallDecision {
        override val allowed = false
    }
}

/** 安装前判断：不自动降级，签名不一致不覆盖，包名必须与预期一致。 */
object InstallPolicy {

    fun decide(candidate: ApkInfo, installed: ApkInfo?, expectedPackageName: String): InstallDecision {
        if (candidate.packageName != expectedPackageName) {
            return InstallDecision.RejectedPackageMismatch(expectedPackageName, candidate.packageName)
        }
        if (installed == null) return InstallDecision.NewInstall
        require(installed.packageName == candidate.packageName) { "已安装信息与安装包的包名不一致" }

        if (!signaturesCompatible(candidate, installed)) return InstallDecision.RejectedSignatureMismatch
        return when {
            candidate.versionCode > installed.versionCode ->
                InstallDecision.Upgrade(installed.versionCode, candidate.versionCode)
            candidate.versionCode == installed.versionCode -> InstallDecision.Reinstall(candidate.versionCode)
            else -> InstallDecision.RejectedDowngrade(installed.versionCode, candidate.versionCode)
        }
    }

    fun signaturesCompatible(candidate: ApkInfo, installed: ApkInfo): Boolean {
        if (candidate.signers.isEmpty() || installed.signers.isEmpty()) return false
        if (candidate.signers == installed.signers) return true
        // 单签名应用做过密钥轮换时，新包的轮换历史里包含已安装版本的证书即可升级
        return !candidate.hasMultipleSigners &&
            !installed.hasMultipleSigners &&
            candidate.signerHistory.containsAll(installed.signers)
    }
}
