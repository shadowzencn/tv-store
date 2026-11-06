<?php

namespace App\Services;

use RuntimeException;

class VersionRuleException extends RuntimeException
{
    public const PACKAGE_MISMATCH = 'package_mismatch';

    public const SIGNER_MISMATCH = 'signer_mismatch';

    public const VERSION_CODE_DOWNGRADE = 'version_code_downgrade';

    public const DUPLICATE_VERSION_CODE = 'duplicate_version_code';

    public const DUPLICATE_FILE = 'duplicate_file';

    public const INVALID_STATUS = 'invalid_status';

    public function __construct(public readonly string $reason, string $message)
    {
        parent::__construct($message);
    }
}
