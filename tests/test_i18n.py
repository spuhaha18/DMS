import pytest
from django.conf import settings
from django.test import Client
from django.utils.translation import override


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


def test_documents_models_use_korean_verbose_names():
    from documents.models import Document, DocumentRevision, DocumentStatus
    with override("ko"):
        assert str(Document._meta.verbose_name) == "문서"
        assert str(DocumentRevision._meta.verbose_name) == "리비전"
        labels = dict(DocumentStatus.choices)
        assert str(labels["Approved"]) == "승인됨"
        assert str(labels["Effective"]) == "발효됨"


def test_approvals_models_korean():
    from approvals.models import ApprovalTask, ApprovalTaskStatus, ElectronicSignature
    with override("ko"):
        assert str(ApprovalTask._meta.verbose_name) == "결재 작업"
        assert str(ElectronicSignature._meta.verbose_name) == "전자서명"
        labels = dict(ApprovalTaskStatus.choices)
        assert str(labels["Approved"]) == "승인"
        assert str(labels["Rejected"]) == "반려"


def test_remaining_models_korean():
    from audit.models import AuditEvent
    from pdfs.models import PdfConversionJob, PdfConversionStatus, QaException
    from viewer.models import ViewEvent
    with override("ko"):
        assert str(AuditEvent._meta.verbose_name) == "감사 이벤트"
        assert str(PdfConversionJob._meta.verbose_name) == "PDF 변환 작업"
        assert str(QaException._meta.verbose_name) == "QA 예외"
        assert str(ViewEvent._meta.verbose_name) == "열람 이벤트"
        labels = dict(PdfConversionStatus.choices)
        assert str(labels["Succeeded"]) == "성공"


def test_admin_site_korean_branding():
    from django.contrib import admin
    with override("ko"):
        assert str(admin.site.site_header) == "EDMS 관리자"
        assert str(admin.site.site_title) == "EDMS"
        assert str(admin.site.index_title) == "운영 콘솔"


def test_forms_have_korean_labels():
    from approvals.forms import ApprovalDecisionForm
    from documents.forms import DocumentRegistrationForm
    with override("ko"):
        df = DocumentRegistrationForm()
        assert str(df.fields["title"].label) == "제목"
        assert str(df.fields["source_file"].label) == "원본 파일"
        af = ApprovalDecisionForm()
        assert str(af.fields["decision"].label) == "결정"
        choices = dict(af.fields["decision"].choices)
        assert str(choices["approve"]) == "승인"
        assert str(choices["reject"]) == "반려"


def test_viewer_access_denied_message_korean():
    import inspect

    from viewer import views
    src = inspect.getsource(views)
    assert "접근이 거부되었습니다" in src
    assert "공식 PDF가 아직 발급되지 않았습니다" in src
    assert "PDF" in src and ("로드 실패" in src or "열 수 없습니다" in src)


@pytest.mark.django_db
def test_base_template_korean_navigation():
    from django.contrib.auth.models import User
    User.objects.create_user("nav", password="pw")
    c = Client()
    c.login(username="nav", password="pw")
    resp = c.get("/documents/")
    body = resp.content.decode("utf-8")
    assert "문서 등록" in body
    assert "결재함" in body
    assert "감사 로그" in body
    assert "관리자" in body
    assert 'lang="ko"' in body


@pytest.mark.django_db
def test_login_page_korean():
    c = Client()
    resp = c.get("/accounts/login/")
    body = resp.content.decode("utf-8")
    assert "EDMS 로그인" in body
    assert "사용자명" in body
    assert "비밀번호" in body
    assert "로그인" in body
    assert 'lang="ko"' in body


@pytest.mark.django_db
def test_logged_out_page_korean():
    from django.contrib.auth.models import User
    User.objects.create_user("lo", password="pw")
    c = Client()
    c.login(username="lo", password="pw")
    # LOGOUT_REDIRECT_URL="login" causes redirect to login page after logout
    resp = c.post("/accounts/logout/", follow=True)
    body = resp.content.decode("utf-8")
    assert "로그인" in body


@pytest.mark.django_db
def test_documents_register_page_korean():
    from django.contrib.auth.models import User
    User.objects.create_user("dr", password="pw")
    c = Client()
    c.login(username="dr", password="pw")
    resp = c.get("/documents/")
    body = resp.content.decode("utf-8")
    assert "문서 등록부" in body
    assert "번호 또는 제목으로 검색" in body
    assert "신규 문서" in body
    assert "검색" in body
