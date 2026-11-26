# Базовый образ содержит edge-tts 7.2.7, который получает 403 от сервера
# Microsoft (устаревший Sec-MS-GEC-Version). Обновляем библиотеку до рабочей.
FROM travisvn/openai-edge-tts:latest-ffmpeg
RUN pip install --no-cache-dir --upgrade "edge-tts==7.3.1"
