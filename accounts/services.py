from django.core.exceptions import ValidationError


def require_password_reentry(user, password: str) -> None:
    if not user.check_password(password):
        raise ValidationError("Password re-entry failed.")
    if not user.is_active:
        raise ValidationError("Inactive users cannot sign.")
