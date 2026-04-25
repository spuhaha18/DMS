from ldap3 import Server, Connection, ALL, SUBTREE
from app.config import settings


def authenticate(username: str, password: str) -> dict | None:
    """Bind to AD and return user info dict, or None on failure."""
    server = Server(settings.LDAP_HOST, get_info=ALL)
    user_dn = f"{username}@{settings.LDAP_DOMAIN}"
    try:
        conn = Connection(server, user=user_dn, password=password, auto_bind=True)
    except Exception:
        return None
    conn.search(
        settings.LDAP_BASE_DN,
        f"(sAMAccountName={username})",
        search_scope=SUBTREE,
        attributes=["sAMAccountName", "mail", "department", "title", "memberOf"],
    )
    if not conn.entries:
        return None
    e = conn.entries[0]
    return {
        "username": str(e.sAMAccountName),
        "email": str(e.mail),
        "department": str(e.department),
        "title": str(e.title),
        "groups": [str(g) for g in e.memberOf],
    }
