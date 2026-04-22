<style type="text/css">
    .pdf-drop-zone {
        border: 2px dashed #007bff;
        border-radius: 5px;
        padding: 40px;
        text-align: center;
        background: #f8f9fa;
        cursor: pointer;
    }

    /* Scoped Overlay */
    #full-page-preview-${menu.properties.id!} {
        display: none;
        position: fixed;
        top: 0; left: 0;
        width: 100vw; height: 100vh;
        background: rgba(0,0,0,0.8);
        z-index: 9999;
        flex-direction: column;
    }

    #full-page-preview-${menu.properties.id!} iframe {
        width: 90%; height: 85%;
        margin: auto; border: none; background: white;
    }

    .close-preview {
        position: absolute; top: 20px; right: 40px;
        color: white; font-size: 30px; cursor: pointer;
        font-family: Arial, sans-serif;
    }

    .loader {
        display: none; color: white;
        text-align: center; margin-top: 20%;
    }
</style>

<div class="form-cell">
    <label class="label">${menu.properties.label!""}</label>

    <div id="drop-zone-${menu.properties.id!}" class="pdf-drop-zone">
        <p>Drag & Drop PDF to Compress and Preview Full Page</p>
        <input id="input-${menu.properties.id!}" type="file" accept="application/pdf" style="display:none;" />
    </div>

    <div id="full-page-preview-${menu.properties.id!}">
        <span class="close-preview" id="close-btn-${menu.properties.id!}">&times; Close Preview</span>
        <div class="loader" id="pdf-loader-${menu.properties.id!}">Processing and Compressing PDF...</div>
        <iframe id="full-viewer-${menu.properties.id!}"></iframe>
    </div>
</div>

<script type="text/javascript">
(function() {
    const id = "${menu.properties.id!}";
    const dropZone = document.getElementById('drop-zone-' + id);
    const fileInput = document.getElementById('input-' + id);
    const overlay = document.getElementById('full-page-preview-' + id);
    const viewer = document.getElementById('full-viewer-' + id);
    const loader = document.getElementById('pdf-loader-' + id);
    const closeBtn = document.getElementById('close-btn-' + id);

    if (!dropZone) return;

    dropZone.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) uploadAndPreview(e.target.files[0]);
    });

    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); });
    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        if (e.dataTransfer.files.length > 0) uploadAndPreview(e.dataTransfer.files[0]);
    });

    function uploadAndPreview(file) {
        const maxSizeMB = parseInt("${menu.properties.maxSize!0}");
        if (maxSizeMB > 0) {
            const fileSizeMB = file.size / (1024 * 1024);
            if (fileSizeMB > maxSizeMB) {
                const msg = "${menu.properties.maxSizeMsg!'File is too big'}";
                alert(msg + " (Maks: " + maxSizeMB + " MB)");
                return;
            }
        }

        overlay.style.display = 'flex';
        loader.style.display = 'block';
        viewer.style.display = 'none'; // Hide old PDF
        viewer.src = "about:blank";

        const formData = new FormData();
        formData.append('pdfFile', file);

        const csrfToken = (typeof ConnectionManager !== 'undefined') ? ConnectionManager.tokenValue : '';
        const csrfName = (typeof ConnectionManager !== 'undefined') ? ConnectionManager.tokenName : '';

        const xhr = new XMLHttpRequest();

        // Track progress upload
        xhr.upload.addEventListener('progress', (e) => {
            if (e.lengthComputable) {
                const pct = Math.round((e.loaded / e.total) * 100);
                loader.textContent = 'Uploading... ' + pct + '%';
            }
        });

        // Server selesai kompresi
        xhr.upload.addEventListener('load', () => {
            loader.textContent = 'Compressing PDF on server...';
        });

        xhr.onload = function () {
            if (xhr.status === 200) {
                const blob = new Blob([xhr.response], { type: 'application/pdf' });
                if (blob.size === 0) {
                    alert('Server returned 0 bytes. Check Java logs.');
                    overlay.style.display = 'none';
                    return;
                }
                const url = URL.createObjectURL(blob);
                loader.style.display = 'none';
                viewer.style.display = 'block';
                viewer.src = url;
            } else {
                const errorText = new TextDecoder().decode(new Uint8Array(xhr.response));
                console.error('Server error:', errorText);
                alert('Error ' + xhr.status + ': ' + errorText);
                overlay.style.display = 'none';
                loader.style.display = 'none';
            }
        };

        xhr.onerror = function () {
            alert('Network error. Check server.');
            overlay.style.display = 'none';
        };

        const level = "${menu.properties.compressionLevel!'medium'}";
        const enableWatermark = "${menu.properties.enableWatermark!'false'}";
        const watermarkText = "${menu.properties.watermarkText!'PREVIEW'}";

        xhr.open('POST', '${request.contextPath}/web/json/plugin/${className}/service'
            + '?level=' + encodeURIComponent(level)
            + '&watermark=' + encodeURIComponent(enableWatermark)
            + '&watermarkText=' + encodeURIComponent(watermarkText)
        );
        xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');
        if (csrfName && csrfToken) {
            xhr.setRequestHeader(csrfName, csrfToken);
        }
        xhr.responseType = 'arraybuffer';
        xhr.send(formData);
    }

    closeBtn.addEventListener('click', () => {
        overlay.style.display = 'none';
        viewer.src = "";
        // Revoke the URL to free memory
        if (viewer.src.startsWith('blob:')) {
            URL.revokeObjectURL(viewer.src);
        }
    });
})();
</script>