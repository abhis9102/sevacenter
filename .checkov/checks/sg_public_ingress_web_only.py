"""CKV_SEVA_1: only HTTP/HTTPS may be reachable from the whole internet.

Checkov's built-ins cover single ports (22: CKV_AWS_24, 3389: CKV_AWS_25, all ports: CKV_AWS_277)
but nothing stops a security group opening, say, Postgres 5432 to 0.0.0.0/0. CR-3 says the
database is private, and in general only the load balancer faces the internet, on 80 (redirect)
and 443. So: any ingress from 0.0.0.0/0 or ::/0 must be exactly port 80 or 443 over TCP.

Covers the three ways Terraform writes ingress: inline blocks in aws_security_group,
aws_security_group_rule (type = "ingress") and aws_vpc_security_group_ingress_rule.
A CIDR given as a variable can't be resolved here and is not treated as public; scanning the
plan (round 2) resolves those.
"""

from checkov.common.models.enums import CheckCategories, CheckResult
from checkov.terraform.checks.resource.base_resource_check import BaseResourceCheck

INTERNET = {"0.0.0.0/0", "::/0"}
WEB_PORTS = {80, 443}


def _flat(value):
    """HCL attributes arrive wrapped in lists (sometimes nested); return the scalars."""
    if isinstance(value, list):
        return [x for v in value for x in _flat(v)]
    return [] if value is None else [value]


def _first(value):
    flat = _flat(value)
    return flat[0] if flat else None


def _port(value):
    try:
        return int(_first(value))
    except (TypeError, ValueError):
        return None


class SecurityGroupPublicIngressWebOnly(BaseResourceCheck):
    def __init__(self):
        super().__init__(
            name="Ingress from the internet (0.0.0.0/0, ::/0) is allowed only on TCP 80 and 443",
            id="CKV_SEVA_1",
            categories=[CheckCategories.NETWORKING],
            supported_resources=[
                "aws_security_group",
                "aws_security_group_rule",
                "aws_vpc_security_group_ingress_rule",
            ],
        )

    def scan_resource_conf(self, conf):
        if self.entity_type == "aws_security_group":
            rules = [r for r in _flat(conf.get("ingress")) if isinstance(r, dict)]
        elif self.entity_type == "aws_security_group_rule":
            rules = [conf] if _first(conf.get("type")) == "ingress" else []
        else:
            rules = [conf]
        for rule in rules:
            if self._public(rule) and not self._web_only(rule):
                return CheckResult.FAILED
        return CheckResult.PASSED

    @staticmethod
    def _public(rule):
        cidrs = set()
        for key in ("cidr_blocks", "ipv6_cidr_blocks", "cidr_ipv4", "cidr_ipv6"):
            cidrs.update(str(c) for c in _flat(rule.get(key)))
        return bool(cidrs & INTERNET)

    @staticmethod
    def _web_only(rule):
        protocol = str(_first(rule.get("protocol", rule.get("ip_protocol"))) or "").lower()
        if protocol not in ("tcp", "6"):
            return False  # "-1"/"all" opens every port; UDP isn't web traffic
        low, high = _port(rule.get("from_port")), _port(rule.get("to_port"))
        return low is not None and low == high and low in WEB_PORTS


check = SecurityGroupPublicIngressWebOnly()
