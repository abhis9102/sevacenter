"""CKV_SEVA_2: Terraform never creates long-lived IAM access keys (CR-1).

CKV_AWS_273 flags IAM users, but nothing flags the key itself, and the key is the credential that
leaks: it never expires, and Terraform writes its secret into the state file. People sign in
through SSO, CI through GitHub OIDC, services through their task role; none of them needs one.
"""

from checkov.common.models.enums import CheckCategories, CheckResult
from checkov.terraform.checks.resource.base_resource_check import BaseResourceCheck


class NoIamAccessKeys(BaseResourceCheck):
    def __init__(self):
        super().__init__(
            name="No long-lived IAM access keys: use SSO, OIDC or an IAM role instead",
            id="CKV_SEVA_2",
            categories=[CheckCategories.IAM],
            supported_resources=["aws_iam_access_key"],
        )

    def scan_resource_conf(self, conf):
        return CheckResult.FAILED


check = NoIamAccessKeys()
