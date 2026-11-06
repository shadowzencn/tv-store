package com.shadowzen.tvstore.core.installer

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallPolicyTest {

    private val pkg = "com.example.target"

    private fun apk(
        versionCode: Long,
        signer: String = "aaaa",
        history: Set<String> = setOf(signer),
        packageName: String = pkg,
    ) = ApkInfo(packageName, versionCode, "v$versionCode", setOf(signer), history, hasMultipleSigners = false)

    @Test
    fun notInstalled_isNewInstall() {
        assertEquals(InstallDecision.NewInstall, InstallPolicy.decide(apk(1), null, pkg))
    }

    @Test
    fun higherVersionSameSigner_isUpgrade() {
        assertEquals(InstallDecision.Upgrade(1, 2), InstallPolicy.decide(apk(2), apk(1), pkg))
    }

    @Test
    fun sameVersion_isReinstall() {
        assertEquals(InstallDecision.Reinstall(2), InstallPolicy.decide(apk(2), apk(2), pkg))
    }

    @Test
    fun lowerVersion_isRejectedAsDowngrade() {
        val decision = InstallPolicy.decide(apk(1), apk(2), pkg)
        assertEquals(InstallDecision.RejectedDowngrade(2, 1), decision)
        assertFalse(decision.allowed)
    }

    @Test
    fun differentSigner_isRejectedEvenForUpgrade() {
        val decision = InstallPolicy.decide(apk(2, signer = "bbbb"), apk(1, signer = "aaaa"), pkg)
        assertEquals(InstallDecision.RejectedSignatureMismatch, decision)
        assertFalse(decision.allowed)
    }

    @Test
    fun rotatedKeyWithInstalledSignerInHistory_isAllowed() {
        val candidate = apk(2, signer = "new", history = setOf("old", "new"))
        assertEquals(InstallDecision.Upgrade(1, 2), InstallPolicy.decide(candidate, apk(1, signer = "old"), pkg))
    }

    @Test
    fun emptySigners_areNeverCompatible() {
        val unsigned = ApkInfo(pkg, 2, "2", emptySet(), emptySet(), false)
        assertFalse(InstallPolicy.signaturesCompatible(unsigned, unsigned))
    }

    @Test
    fun unexpectedPackage_isRejected() {
        val decision = InstallPolicy.decide(apk(1, packageName = "com.other"), null, pkg)
        assertEquals(InstallDecision.RejectedPackageMismatch(pkg, "com.other"), decision)
    }

    @Test
    fun allowedDecisions_areMarkedAllowed() {
        assertTrue(InstallDecision.NewInstall.allowed)
        assertTrue(InstallDecision.Upgrade(1, 2).allowed)
        assertTrue(InstallDecision.Reinstall(1).allowed)
    }
}

class InstallOutcomeTest {

    @Test
    fun mapsFinalStatuses() {
        assertEquals(InstallOutcome.Success, installOutcomeOf(PackageInstaller.STATUS_SUCCESS, null))
        assertEquals(InstallOutcome.Cancelled, installOutcomeOf(PackageInstaller.STATUS_FAILURE_ABORTED, null))
        val conflict = installOutcomeOf(PackageInstaller.STATUS_FAILURE_CONFLICT, "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        assertTrue(conflict is InstallOutcome.Failed)
        assertEquals("INSTALL_FAILED_UPDATE_INCOMPATIBLE", (conflict as InstallOutcome.Failed).systemMessage)
    }

    @Test
    fun unknownStatus_includesCode() {
        val outcome = installOutcomeOf(12345, null) as InstallOutcome.Failed
        assertTrue(outcome.reason.contains("12345"))
    }
}
