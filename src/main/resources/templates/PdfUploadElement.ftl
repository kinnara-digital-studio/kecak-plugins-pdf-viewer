<style type="text/css">
    /* Container utama agar tidak merusak layout Navigasi */
    .pdf-upload-wrapper-${elementParamName!} {
        display: block;
        clear: both;
        width: 100%;
        margin-bottom: 20px;
    }

    /* Area Drop Zone */
    #drop-zone-${elementParamName!} {
        border: 2px dashed #007bff;
        border-radius: 6px;
        padding: 40px;
        text-align: center;
        background: #f8f9fa;
        cursor: pointer;
        transition: all 0.2s ease-in-out;
    }

    #drop-zone-${elementParamName!}:hover,
    #drop-zone-${elementParamName!}.drag-over {
        background: #e3f0ff;
        border-color: #0056b3;
    }

    /* Daftar File yang muncul di bawah Drop Zone */
    .pdf-file-list-${elementParamName!} {
        margin-top: 15px;
        padding: 0;
        list-style: none;
    }

    .pdf-file-list-${elementParamName!} li {
        display: flex;
        align-items: center;
        padding: 10px;
        background: #fff;
        border: 1px solid #ddd;
        border-radius: 4px;
        margin-bottom: 8px;
        box-shadow: 0 1px 3px rgba(0,0,0,0.05);
    }

    .pdf-preview-link {
        margin-left: auto;
        margin-right: 15px;
        font-size: 13px;
        color: #007bff;
        cursor: pointer;
        text-decoration: underline;
        font-weight: bold;
    }

    /* Overlay Modal Preview - Menutup seluruh layar termasuk Navigasi */
    #full-page-preview-${elementParamName!} {
        display: none;
        position: fixed;
        top: 0; left: 0;
        width: 100vw; height: 100vh;
        background: rgba(0,0,0,0.9);
        z-index: 10000;
        flex-direction: column;
        align-items: center;
        justify-content: center;
    }

    #full-page-preview-${elementParamName!} iframe {
        width: 90%;
        height: 85%;
        border: none;
        background: white;
    }

    #close-btn-${elementParamName!} {
        position: absolute;
        top: 20px; right: 40px;
        color: white;
        font-size: 32px;
        cursor: pointer;
        font-family: Arial, sans-serif;
    }

    .loader-msg { color: white; margin-bottom: 10px; font-family: sans-serif; }
</style>

<div class="form-cell pdf-upload-wrapper-${elementParamName!}" ${elementMetaData!}>
    <label class="label">
        ${element.properties.label!}
        <span class="form-cell-validator">${decoration}</span>
        <#if error??><span class="form-error-message">${error}</span></#if>
    </label>

    <div class="form-fileupload">
        <#if element.properties.readonly! != 'true'>
            <div id="drop-zone-${elementParamName!}">
                <p style="margin:0; font-size:16px;">Drag & Drop PDF atau <strong>Klik untuk Upload</strong></p>
                <input id="input-${elementParamName!}"
                       type="file"
                       accept="application/pdf"
                       <#if element.properties.multiple! == 'true'>multiple</#if>
                       style="display:none;"/>
            </div>
        </#if>
        <ul id="file-list-${elementParamName!}" class="pdf-file-list-${elementParamName!}">
            <#-- 1. FILE YANG SUDAH TERSIMPAN DI DATABASE (EDIT MODE) -->
            <#if filePaths??>
                <#list filePaths?keys as key>
                    <li>
                        <a href="${request.contextPath}${key!?html}" target="_blank" >📄 ${filePaths[key]!?html}</a>
                        <input type="hidden" name="${elementParamName!}_path" value="${filePaths[key]!?html}"/>
                        <#if element.properties.readonly! != 'true'>
                            <input type="checkbox" name="${elementParamName!}_remove" value="${filePaths[key]!?html}" /> <span style="font-size:smaller">@@form.fileupload.remove@@</span>
                        </#if>
                    </li>
                </#list>
            </#if>
        </ul>

        <div id="full-page-preview-${elementParamName!}">
            <span id="close-btn-${elementParamName!}">&times; Tutup Preview</span>
            <div id="pdf-loader-${elementParamName!}" class="loader-msg">Sedang memproses PDF...</div>
            <iframe id="full-viewer-${elementParamName!}"></iframe>
        </div>
    </div>
</div>

<script type="text/javascript">
(function() {
    const paramName = "${elementParamName!}";
    const isMultiple = "${element.properties.multiple!}" === "true";

    const dropZone  = document.getElementById('drop-zone-' + paramName);
    const fileInput = document.getElementById('input-' + paramName);
    const overlay   = document.getElementById('full-page-preview-' + paramName);
    const viewer    = document.getElementById('full-viewer-' + paramName);
    const fileList  = document.getElementById('file-list-' + paramName);

    /* --- 1. Definisikan Global Constants agar bisa diakses semua fungsi --- */
    const contextPath = "${request.contextPath!}";
    const className   = "${className!}";
    const appId       = "${appId!}";
    const appVersion  = "${appVersion!}";
    const tableName   = "${tableName!}";

    if (!dropZone) return;

    // Aksi Klik & Pilih File
    dropZone.onclick = () => fileInput.click();
    fileInput.onchange = (e) => { Array.from(e.target.files).forEach(file => startUpload(file)); };

    // Aksi Drag & Drop
    dropZone.addEventListener('dragover', (e) => { e.preventDefault(); dropZone.classList.add('drag-over'); });
    dropZone.addEventListener('dragleave', () => { dropZone.classList.remove('drag-over'); });
    dropZone.addEventListener('drop', (e) => {
        e.preventDefault();
        dropZone.classList.remove('drag-over');
        Array.from(e.dataTransfer.files).forEach(file => startUpload(file));
    });

    function startUpload(file) {
        if (file.type !== 'application/pdf') { alert('Hanya file PDF!'); return; }

        overlay.style.display = 'flex';
        document.getElementById('pdf-loader-' + paramName).style.display = 'block';
        viewer.style.display = 'none';

        const formData = new FormData();
        formData.append('pdfFile', file);

        const xhr = new XMLHttpRequest();
        xhr.onload = function() {
            if (xhr.status === 200) {
                try {
                    // Pastikan Java mengirim JSON: {"path":"...", "filename":"..."}
                    const resp = JSON.parse(xhr.responseText);
                    if (resp.error) {
                        alert("Error Server: " + resp.error);
                        overlay.style.display = 'none';
                    } else {
                        // 1. Tambahkan ke daftar (Agar Ready to Save)
                        addFileToForm(resp.filename, resp.path);

                        // 2. Jalankan Preview
                        const previewUrl = '${request.contextPath}/web/json/plugin/${className!}/service'
                                         + '?_path=' + encodeURIComponent(resp.path)
                                         + '&_paramName=' + paramName;
                        viewer.src = previewUrl;
                        viewer.style.display = 'block';
                        document.getElementById('pdf-loader-' + paramName).style.display = 'none';
                    }
                } catch (e) {
                    console.error("Respon bukan JSON! Isinya:", xhr.responseText);
                    alert("Gagal memproses respon. Cek Java WebService (harus return JSON).");
                    overlay.style.display = 'none';
                }
            }
        };

        xhr.open('POST', '${request.contextPath}/web/json/plugin/${className!}/service?_paramName=' + paramName);
        if (typeof ConnectionManager !== 'undefined') {
            xhr.setRequestHeader(ConnectionManager.tokenName, ConnectionManager.tokenValue);
        }
        xhr.send(formData);
    }

    function addFileToForm(name, path) {
        <#noparse>
        // Jika tidak multiple, hapus file lama dari list
        if (!isMultiple) { fileList.innerHTML = ''; }

        const li = document.createElement('li');
        // Menggunakan string concatenation biasa agar tidak bentrok dengan FreeMarker ${}
        var html = '<span>📄 ' + name + '</span>';
        html += '<input type="hidden" name="' + paramName + '_path" value="' + path + '"/>';
        html += '<span class="pdf-preview-link" onclick="previewTempPdf(\'' + paramName + '\', \'' + path + '\')">Preview</span>';
        html += '<label style="margin-left:15px;"><input type="checkbox" name="' + paramName + '_remove" value="' + path + '"/> Hapus</label>';

        li.innerHTML = html;
        fileList.appendChild(li);
        </#noparse>
    }

    document.getElementById('close-btn-' + paramName).onclick = () => {
        overlay.style.display = 'none';
        viewer.src = '';
    };

    window.previewTempPdf = function(pName, path) {
        if (pName !== paramName) return;
        overlay.style.display = 'flex';
        document.getElementById('pdf-loader-' + paramName).style.display = 'none';
        viewer.style.display = 'block';
        viewer.src = '${request.contextPath}/web/json/plugin/${className!}/service?_path=' + encodeURIComponent(path) + '&_paramName=' + paramName;
    };

    window.previewSavedPdf = function(pName, fileName) {
        if (pName !== paramName) return;

        overlay.style.display = 'flex';

        // Ambil Table Name dari properti elemen (pastikan di Java renderTemplate sudah dimasukkan)
        const tableName = "${tableName!}";
        const recordId  = "${element.properties.id!}";

        const url = contextPath + '/web/json/plugin/' + className + '/service'
                  + '?_path=' + encodeURIComponent(fileName)
                  + '&_tableName=' + tableName
                  + '&_id=' + recordId;

        viewer.src = url;
    };
})();
</script>