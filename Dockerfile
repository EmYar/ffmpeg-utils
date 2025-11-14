FROM alpine:3.20

ARG APP_VERSION
ARG PLATFORM_TAG

ENV APP_VERSION=${APP_VERSION}
ENV PLATFORM_TAG=${PLATFORM_TAG}

ENV FFTOOLS_PREFIX=""

LABEL org.opencontainers.image.title="ffmpeg-utils" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.vendor="local" \
      org.opencontainers.image.revision="${PLATFORM_TAG}"

COPY build/distributions/ffmpeg-utils /usr/local/bin/ffmpeg-utils

RUN chmod +x /usr/local/bin/ffmpeg-utils

ENTRYPOINT ["/usr/local/bin/ffmpeg-utils"]
