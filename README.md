# EDMS

Electronic Document Management System built with Django 5.x.

## 요구 사항

- Python 3.12+
- LibreOffice (`soffice` 바이너리)

### 한글 폰트

PDF 변환 시 한글이 올바르게 렌더링되려면 한글 폰트가 필요합니다.

```bash
apt-get install fonts-noto-cjk fonts-nanum
```

## 설치

```bash
pip install -e ".[test]"
python manage.py migrate
python manage.py createsuperuser
```

## 한국어 번역 빌드

배포 전 또는 소스 문자열이 변경될 때마다 실행하세요. CI 파이프라인에 포함하는 것을 권장합니다.

```bash
python manage.py compilemessages
```

번역 소스(.po)를 새로 추출할 때:

```bash
python manage.py makemessages -l ko --ignore .venv --ignore venv
python manage.py compilemessages
```

## 결재 페이지 양식 작성

.docx 양식에 결재 페이지 placeholder를 넣는 방법은 [`docs/approval_template_guide.md`](docs/approval_template_guide.md)를 참고하세요.

## 개발 서버

```bash
python manage.py runserver
```

## 테스트

```bash
pytest -v
```
