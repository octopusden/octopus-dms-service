import {NonIdealState, Spinner} from "@blueprintjs/core";
import React from "react";
import {isHtml} from '../common'

export default function preview(props) {
    const { selectedDocument, loadingDocumentArtifact } = props
    if (loadingDocumentArtifact) {
        return <div className='preview-wrapper'>
            <Spinner
                size={50}
                intent="primary"
            />
        </div>
    }

    if (selectedDocument && selectedDocument.isPrintable === false) {
        return <div className='preview-wrapper'>
            <NonIdealState
                icon="manual"
                title="Preview unavailable"
                description="please download artifact"
            />
        </div>
    }

    if (selectedDocument && selectedDocument.documentText) {
        const { documentText } = selectedDocument
        if (isHtml(selectedDocument.displayName)) {
            return <div dangerouslySetInnerHTML={{__html: documentText}}/>
        } else {
            return <pre>{documentText}</pre>
        }
    }

    return <div className='preview-wrapper'>
        <NonIdealState
            icon="manual"
            title="Select document"
            description="Select document for specified component and version to see preview"
        />
    </div>
}
