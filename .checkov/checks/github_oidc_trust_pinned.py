"""CKV_SEVA_3: a GitHub Actions OIDC trust names exactly one repository and context (CR-6).

Any workflow on GitHub can get a token from token.actions.githubusercontent.com. The only thing
that keeps other people's workflows out of a role is the trust policy's `sub` condition, so it
must be an exact match (StringEquals, no wildcard) on `repo:<owner>/<name>:<context>`.

The built-in CKV_AWS_358 only recognises a GitHub trust when the principal is a literal ARN string.
Written the usual way, `identifiers = [aws_iam_openid_connect_provider.github.arn]`, the reference
doesn't resolve, the check decides it isn't a GitHub trust and passes, even for `repo:*`. This
check keys on the condition instead: any statement that tests a `token.actions...` claim, or
trusts a federated principal for AssumeRoleWithWebIdentity without any claim, is inspected.
"""

import json
import re

from checkov.common.models.enums import CheckCategories, CheckResult
from checkov.terraform.checks.data.base_check import BaseDataCheck
from checkov.terraform.checks.resource.base_resource_check import BaseResourceCheck

ISSUER = "token.actions.githubusercontent.com"
EXACT_SUB = re.compile(r"^repo:[^/*?]+/[^:*?]+:(pull_request|environment:[^*?]+|ref:refs/(heads|tags)/[^*?]+)$")
NAME = "GitHub OIDC trust: sub must be exactly repo:<owner>/<name>:<context> (StringEquals, no wildcards)"


def first(v):
    while isinstance(v, list) and len(v) == 1:
        v = v[0]
    return v


def flat(v):
    if isinstance(v, list):
        return [x for item in v for x in flat(item)]
    return [v]


def judge(statements):
    """statements: iterable of (actions, conditions), conditions = [(test, variable, [values])]."""
    for actions, conditions in statements:
        gh = [c for c in conditions if ISSUER in str(c[1])]
        web_identity = any("AssumeRoleWithWebIdentity" in str(a) for a in actions)
        if not gh and not web_identity:
            continue
        subs = [c for c in gh if str(c[1]).endswith(":sub")]
        if not subs:
            return CheckResult.FAILED  # GitHub (or an unidentified federated) trust with no sub
        for test, _, values in subs:
            if test != "StringEquals" or not values:
                return CheckResult.FAILED
            if not all(isinstance(v, str) and EXACT_SUB.match(v) for v in values):
                return CheckResult.FAILED
    return CheckResult.PASSED


class OidcTrustDocument(BaseDataCheck):
    def __init__(self):
        super().__init__(name=NAME, id="CKV_SEVA_3", categories=[CheckCategories.IAM],
                         supported_data=["aws_iam_policy_document"])

    def scan_data_conf(self, conf):
        statements = []
        for st in flat(conf.get("statement") or []):
            if not isinstance(st, dict) or first(st.get("effect")) == "Deny":
                continue
            conds = [(first(c.get("test")), first(c.get("variable")), flat(c.get("values")))
                     for c in flat(st.get("condition") or []) if isinstance(c, dict)]
            statements.append((flat(st.get("actions") or []), conds))
        return judge(statements)


class OidcTrustInline(BaseResourceCheck):
    def __init__(self):
        super().__init__(name=NAME, id="CKV_SEVA_3", categories=[CheckCategories.IAM],
                         supported_resources=["aws_iam_role"])

    def scan_resource_conf(self, conf):
        policy = first(conf.get("assume_role_policy"))
        if isinstance(policy, str):
            try:
                policy = json.loads(policy)
            except ValueError:
                return CheckResult.UNKNOWN  # a reference to a policy document: that check covers it
        if not isinstance(policy, dict):
            return CheckResult.UNKNOWN
        statements = []
        for st in flat(policy.get("Statement") or []):
            if not isinstance(st, dict) or st.get("Effect") == "Deny":
                continue
            conds = [(test, var, flat(vals))
                     for test, pairs in (st.get("Condition") or {}).items()
                     for var, vals in pairs.items()]
            statements.append((flat(st.get("Action") or []), conds))
        return judge(statements)


document_check = OidcTrustDocument()
inline_check = OidcTrustInline()
