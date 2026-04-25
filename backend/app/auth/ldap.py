from ldap3 import Server, Connection, ALL, SUBTREE
from ldap3.utils.conv import escape_filter_chars
from app.config import settings


def derive_role(groups: list[str]) -> str:
    for g in groups:
        if "DMS-Admin" in g:
            return "Admin"
    for g in groups:
        if "DMS-Approver" in g:
            return "Approver"
    for g in groups:
        if "DMS-Reviewer" in g:
            return "Reviewer"
    return "Author"


def authenticate(username: str, password: str) -> dict | None:
    """Bind to AD and return user info dict, or None on failure."""
    server = Server(settings.LDAP_HOST, get_info=ALL)
    user_dn = f"{username}@{settings.LDAP_DOMAIN}"
    try:
        conn = Connection(server, user=user_dn, password=password, auto_bind=True)
    except Exception:
        return None
    try:
        conn.search(
            settings.LDAP_BASE_DN,
            f"(sAMAccountName={escape_filter_chars(username)})",
            search_scope=SUBTREE,
            attributes=["sAMAccountName", "mail", "department", "title", "memberOf"],
        )
        if not conn.entries:
            return None
        e = conn.entries[0]
        groups = [str(g) for g in e.memberOf]
        return {
            "username": str(e.sAMAccountName),
            "email": str(e.mail),
            "department": str(e.department),
            "title": str(e.title),
            "groups": groups,
            "role": derive_role(groups),
        }
    finally:
        conn.unbind()
