from pathlib import Path
import os

from dotenv import load_dotenv

BASE_DIR = Path(__file__).resolve().parent.parent
load_dotenv(BASE_DIR / ".env")

SECRET_KEY = os.getenv("DJANGO_SECRET_KEY", "dev-only-secret-key")
DEBUG = os.getenv("DJANGO_DEBUG", "1") == "1"
ALLOWED_HOSTS = [host for host in os.getenv("DJANGO_ALLOWED_HOSTS", "localhost,127.0.0.1").split(",") if host]

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "accounts",
    "audit",
    "documents",
    "approvals",
    "pdfs",
    "viewer",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.locale.LocaleMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"
TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [BASE_DIR / "templates"],
        "APP_DIRS": True,
        "OPTIONS": {"context_processors": [
            "django.template.context_processors.debug",
            "django.template.context_processors.request",
            "django.contrib.auth.context_processors.auth",
            "django.contrib.messages.context_processors.messages",
        ]},
    }
]
WSGI_APPLICATION = "config.wsgi.application"
ASGI_APPLICATION = "config.asgi.application"

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": BASE_DIR / "db.sqlite3",
    }
}

LANGUAGE_CODE = "ko"
LANGUAGES = [("ko", "한국어")]
TIME_ZONE = "Asia/Seoul"
USE_I18N = True
USE_TZ = True
LOCALE_PATHS = [BASE_DIR / "locale"]

STATIC_URL = "static/"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

LOGIN_URL = "login"
LOGIN_REDIRECT_URL = "documents:register"
LOGOUT_REDIRECT_URL = "login"

EDMS_PRIVATE_MEDIA_ROOT = BASE_DIR / os.getenv("EDMS_PRIVATE_MEDIA_ROOT", "private_media")
EDMS_LIBREOFFICE_BINARY = os.getenv("EDMS_LIBREOFFICE_BINARY", "soffice")
EDMS_DISABLE_DOWNLOADS = os.getenv("EDMS_DISABLE_DOWNLOADS", "1") == "1"
EDMS_ROLE_QA = "QA"
EDMS_ROLE_RESEARCHER = "Researcher"
EDMS_ROLE_REVIEWER = "Reviewer"
EDMS_ROLE_APPROVER = "Approver"
EDMS_ROLE_READER = "Reader"
EDMS_WATERMARK_TEMPLATE = "공식 PDF | {document_number} | Rev {revision} | {status} | 발급일 {generated_at}"
