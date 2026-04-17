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
        // 1. Prepare UI
        overlay.style.display = 'flex';
        loader.style.display = 'block';
        viewer.style.display = 'none'; // Hide old PDF
        viewer.src = "about:blank";

        const formData = new FormData();
        formData.append('pdfFile', file);

        const csrfToken = (typeof ConnectionManager !== 'undefined') ? ConnectionManager.tokenValue : '';
        const csrfName = (typeof ConnectionManager !== 'undefined') ? ConnectionManager.tokenName : '';

        fetch('${request.contextPath}/web/json/plugin/${className}/service', {
            method: 'POST',
            headers: {
                'X-Requested-With': 'XMLHttpRequest',
                [csrfName]: csrfToken
            },
            body: formData
        })
        .then(response => {
            console.log("Response Status:", response.status);
            if (!response.ok) throw new Error('Server Error ' + response.status);
            return response.blob();
        })
        .then(blob => {
            console.log("Received Blob Size:", blob.size, "bytes");

            if (blob.size === 0) {
                throw new Error("Server returned 0 bytes. Check Java logs.");
            }

            const url = URL.createObjectURL(blob);

            // 2. Update UI with new PDF
            loader.style.display = 'none';
            viewer.style.display = 'block';
            viewer.src = url;
        })
        .catch(err => {
            console.error("AJAX Error:", err);
            alert("Error: " + err.message);
            // Hide everything on failure
            overlay.style.display = 'none';
            loader.style.display = 'none';
        });
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