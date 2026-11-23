import { useState, useEffect } from 'react';
import { ExternalLink, Maximize2, X, ChevronLeft, ChevronRight, Image as ImageIcon } from 'lucide-react';

export function ImageGallery({ images = [] }) {
  const [activeImage, setActiveImage] = useState(null);
  const [failedIndices, setFailedIndices] = useState(new Set());
  const [loadedIndices, setLoadedIndices] = useState(new Set());
  const [thumbFallbacks, setThumbFallbacks] = useState(new Set());

  // Lightbox keyboard controls
  useEffect(() => {
    if (activeImage === null) return;
    function handleKeyDown(e) {
      if (e.key === 'Escape') setActiveImage(null);
      if (e.key === 'ArrowRight' && images.length > 1) {
        setActiveImage((prev) => (prev + 1) % images.length);
      }
      if (e.key === 'ArrowLeft' && images.length > 1) {
        setActiveImage((prev) => (prev - 1 + images.length) % images.length);
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [activeImage, images.length]);

  if (!images || images.length === 0) return null;

  const currentModalImage = activeImage !== null ? images[activeImage] : null;

  return (
    <div className="chat-image-gallery-container">
      <div className={`chat-image-grid count-${Math.min(images.length, 4)}`}>
        {images.map((item, index) => {
          const isLoaded = loadedIndices.has(index);
          const isFailed = failedIndices.has(index);
          const useOriginal = thumbFallbacks.has(index);
          const imgSrc = useOriginal ? item.url : (item.thumbnailUrl || item.url);

          return (
            <div
              key={item.url || index}
              className={`chat-image-card ${isFailed ? 'failed' : ''}`}
              onClick={() => {
                if (!isFailed) setActiveImage(index);
              }}
              title={isFailed ? item.title : 'Klik untuk memperbesar gambar'}
            >
              <div className="chat-image-wrapper">
                {!isLoaded && !isFailed && (
                  <div className="chat-image-skeleton">
                    <ImageIcon size={20} className="skeleton-icon" />
                  </div>
                )}
                {!isFailed ? (
                  <img
                    src={imgSrc}
                    alt={item.title || 'Foto'}
                    loading="lazy"
                    className={`chat-image-thumb ${isLoaded ? 'loaded' : ''}`}
                    onLoad={() => setLoadedIndices((prev) => new Set([...prev, index]))}
                    onError={() => {
                      if (!useOriginal && item.thumbnailUrl && item.url && item.thumbnailUrl !== item.url) {
                        // Fallback from thumbnail to original url
                        setThumbFallbacks((prev) => new Set([...prev, index]));
                      } else {
                        // Mark as failed
                        setFailedIndices((prev) => new Set([...prev, index]));
                      }
                    }}
                  />
                ) : (
                  <div className="chat-image-fallback">
                    <ImageIcon size={28} className="fallback-icon" />
                    <span>Gambar Pratinjau</span>
                  </div>
                )}
                {!isFailed && (
                  <div className="chat-image-overlay">
                    <span className="expand-badge">
                      <Maximize2 size={14} />
                      <span>Perbesar</span>
                    </span>
                  </div>
                )}
              </div>

              <div className="chat-image-meta">
                <span className="chat-image-title" title={item.title}>
                  {item.title || 'Gambar'}
                </span>
                <div className="chat-image-footer">
                  <span className="chat-image-source">{item.source || 'Sumber'}</span>
                  {item.sourceUrl && (
                    <a
                      href={item.sourceUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="chat-image-link"
                      onClick={(e) => e.stopPropagation()}
                      title="Kunjungi halaman sumber"
                    >
                      <ExternalLink size={12} />
                    </a>
                  )}
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Lightbox / Fullscreen Modal */}
      {currentModalImage && (
        <div
          className="chat-image-lightbox-backdrop"
          onClick={() => setActiveImage(null)}
          role="dialog"
          aria-modal="true"
        >
          <div
            className="chat-image-lightbox-content"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="lightbox-close-btn"
              onClick={() => setActiveImage(null)}
              aria-label="Tutup pratinjau"
            >
              <X size={20} />
            </button>

            {images.length > 1 && (
              <>
                <button
                  className="lightbox-nav-btn prev"
                  onClick={() =>
                    setActiveImage(
                      (prev) => (prev - 1 + images.length) % images.length
                    )
                  }
                  aria-label="Gambar sebelumnya"
                >
                  <ChevronLeft size={24} />
                </button>
                <button
                  className="lightbox-nav-btn next"
                  onClick={() =>
                    setActiveImage((prev) => (prev + 1) % images.length)
                  }
                  aria-label="Gambar berikutnya"
                >
                  <ChevronRight size={24} />
                </button>
              </>
            )}

            <div className="lightbox-image-wrap">
              <img
                src={currentModalImage.url}
                alt={currentModalImage.title}
                className="lightbox-img"
              />
            </div>

            <div className="lightbox-details">
              <div className="lightbox-info">
                <h3>{currentModalImage.title || 'Pratinjau Gambar'}</h3>
                {currentModalImage.author && (
                  <p className="lightbox-author">
                    Oleh: <span>{currentModalImage.author}</span>
                  </p>
                )}
                <span className="lightbox-badge">{currentModalImage.source || 'Zenvix'}</span>
              </div>
              {currentModalImage.sourceUrl && (
                <a
                  href={currentModalImage.sourceUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="button secondary lightbox-link"
                >
                  <span>Buka Sumber Asli</span>
                  <ExternalLink size={14} />
                </a>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
