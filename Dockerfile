FROM nyanmisaka/jellyfin:latest-rockchip

ARG APP_VERSION
ARG PLATFORM_TAG

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

ENV APP_VERSION=${APP_VERSION}
ENV PLATFORM_TAG=${PLATFORM_TAG}

ENV PARALLELISM=1
ENV DEFAULT_INPUT_PATH_BASE="/in"
ENV DEFAULT_OUTPUT_PATH_BASE="/out"

ENV PATH="/usr/lib/jellyfin-ffmpeg:${PATH}"

LABEL org.opencontainers.image.title="ffmpeg-utils" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.vendor="local" \
      org.opencontainers.image.revision="${PLATFORM_TAG}"

COPY build/distributions/ffmpeg-utils /usr/local/bin/ffmpeg-utils

RUN chmod +x /usr/local/bin/ffmpeg-utils

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/ffmpeg-utils"]
