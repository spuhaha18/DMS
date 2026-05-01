from django.conf import settings


def test_language_code_is_korean():
    assert settings.LANGUAGE_CODE == "ko"


def test_locale_middleware_installed():
    assert "django.middleware.locale.LocaleMiddleware" in settings.MIDDLEWARE


def test_locale_paths_configured():
    paths = [str(p) for p in settings.LOCALE_PATHS]
    assert any(p.endswith("locale") for p in paths)


def test_languages_restricted_to_korean():
    codes = [code for code, _ in settings.LANGUAGES]
    assert codes == ["ko"]


def test_watermark_template_is_korean():
    assert "공식 PDF" in settings.EDMS_WATERMARK_TEMPLATE
