<style type="text/css">
    /* Drop Zone Styling */
    .pdf-drop-zone {
        border: 2px dashed #007bff;
        border-radius: 5px;
        padding: 40px;
        text-align: center;
        background: #f8f9fa;
        cursor: pointer;
    }

    /* FULL PAGE PREVIEW MODAL */
    #full-page-preview {
        display: none;
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: rgba(0,0,0,0.8);
        z-index: 9999;
        flex-direction: column;
    }

    #full-page-preview iframe {
        width: 90%;
        height: 85%;
        margin: auto;
        border: none;
        background: white;
    }

    .close-preview {
        position: absolute;
        top: 20px;
        right: 40px;
        color: white;
        font-size: 30px;
        cursor: pointer;
        font-family: Arial, sans-serif;
    }

    .loader {
        display: none;
        color: white;
        text-align: center;
        margin-top: 20%;
    }
</style>

<div class="form-cell">
    <label class="label">${menu.properties.label!""}</label>

    <div id="drop-zone-${menu.properties.id!}" class="pdf-drop-zone">
        <p>Drag & Drop PDF to Compress and Preview Full Page</p>
        <input id="input-${menu.properties.id!}" type="file" accept="application/pdf" style="display:none;" />
    </div>

    <div id="full-page-preview">
        <span class="close-preview" id="close-btn">&times; Close Preview</span>
        <div class="loader" id="pdf-loader">Processing and Compressing PDF...</div>
        <iframe id="full-viewer"></iframe>
    </div>
</div>

<script type="text/javascript">
(function() {
    const id = "${menu.properties.id!}";
    const dropZone = document.getElementById('drop-zone-' + id);
    const fileInput = document.getElementById('input-' + id);
    const overlay = document.getElementById('full-page-preview');
    const viewer = document.getElementById('full-viewer');
    const loader = document.getElementById('pdf-loader');
    const closeBtn = document.getElementById('close-btn');

    dropZone.addEventListener('click', () => fileInput.click());

    fileInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) uploadAndPreview(e.target.files[0]);
    });

    // Handle Drag and Drop
    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); });
    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        if (e.dataTransfer.files.length > 0) uploadAndPreview(e.dataTransfer.files[0]);
    });

    function uploadAndPreview(file) {
        overlay.style.display = 'flex';
        loader.style.display = 'block';

        const formData = new FormData();
        formData.append('pdfFile', file);

        // Get CSRF Token from Joget's global variable
        const csrfToken = (typeof ConnectionManager !== 'undefined') ? ConnectionManager.tokenValue : '';
        const csrfName = (typeof ConnectionManager !== 'undefined') ? ConnectionManager.tokenName : '';

        fetch('${request.contextPath}/web/json/plugin/${className}/service', {
            method: 'POST',
            headers: {
                'X-Requested-With': 'XMLHttpRequest',
                // Include the security token
                [csrfName]: csrfToken
            },
            body: formData
        })
        .then(response => {
            // LOG 1: Check if the request was successful
                console.log("Response Status:", response.status);
                console.log("Content-Type:", response.headers.get("content-type"));

                if (!response.ok) throw new Error('Server Error ' + response.status);
                return response.blob();
        })
        .then(blob => {
            // LOG 2: Check the size of the PDF received
                console.log("Received Blob Size:", blob.size, "bytes");

                if (blob.size === 0) {
                        alert("Server sent an empty file!");
                        return;
                    }

                    // If the size is small (like < 1000 bytes), it might be a text error instead of a PDF
                    if (blob.size < 1000) {
                        blob.text().then(text => console.warn("Small blob content (Possible Error):", text));
                    }

                    const url = URL.createObjectURL(blob);
                        const viewer = document.getElementById('full-viewer');

                        // Clear the src first to force a refresh
                        viewer.src = "about:blank";

                        setTimeout(() => {
                            viewer.src = url;
                            document.getElementById('full-page-preview').style.display = 'flex';
                            document.getElementById('pdf-loader').style.display = 'none';
                        }, 100);

        })
        .catch(err => {
            overlay.style.display = 'none';
            console.error("AJAX Error:", err);
            document.getElementById('pdf-loader').style.display = 'none';
        });
    }

    closeBtn.addEventListener('click', () => {
        overlay.style.display = 'none';
        viewer.src = "";
    });
})();
</script>