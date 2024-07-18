import {H4, Icon, Spinner} from "@blueprintjs/core";
import {isPrintableArtifact} from "../common";
import React from "react";
import './style.css'

export default function artifactList(props) {
    const {
        loadingArtifactsList, artifactsList, fetchDocumentArtifact, selectedComponent, selectedMinor, selectedVersion,
        selectedDocument, adminMode, deleteArtifact, showConfirmation
    } = props
    if (loadingArtifactsList) {
        return <div className='load-artifacts-list'>
            <Spinner size={50} intent="primary"/>
        </div>
    } else {
        let printableArtifacts = artifactsList.filter(artifact => isPrintableArtifact(artifact));
        let binaryArtifacts = artifactsList.filter(artifact => !isPrintableArtifact(artifact));
        return <div className='artifacts-component-list-block'>
            {printableArtifacts.length > 0 && <H4> Documents </H4>}
            {artifactBlock(printableArtifacts, fetchDocumentArtifact, selectedComponent, selectedMinor, selectedVersion, selectedDocument, adminMode, deleteArtifact, showConfirmation)}
            {binaryArtifacts.length > 0 && <H4> Binaries </H4>}
            {artifactBlock(binaryArtifacts, fetchDocumentArtifact, selectedComponent, selectedMinor, selectedVersion, selectedDocument, adminMode, deleteArtifact, showConfirmation)}
        </div>
    }
}

function ArtifactLabel(props) {
    const {
        fetchDocument, selectedComponent, selectedMinor, selectedVersion,
        selectedDocument, id, displayName, fileName, isPrintable, isDeletable,
        deleteArtifact, showConfirmation
    } = props

    const isSelected = selectedDocument.id === id

    return <div
        className={`artifact-label-wrap ${isSelected ? 'selected' : ''}`}
    >
        {isPrintable
            ? <div className='artifact-label-link'
                   onClick={() => {
                       fetchDocument(selectedComponent, selectedVersion, id, isPrintable, displayName)
                   }}
            >{displayName}</div>
            : <div className='artifact-label-text'>{displayName}</div>
        }

        <div className='artifact-label-right'>
            <a href={`rest/api/3/artifacts/${id}/download`}
               download={fileName}>
                <Icon icon='import'/>
            </a>
            {isPrintable &&
                <a href={`rest/api/3/artifacts/${id}/download`}
                   target="_blank" hidden={!isPrintable}>
                    <Icon icon='share'/>
                </a>
            }
            {isDeletable &&
                <a href="#" onClick={() =>
                    showConfirmation(`Delete artifact ${selectedComponent}:${selectedVersion} ${displayName}?`,
                        () => deleteArtifact(selectedComponent, selectedMinor, selectedVersion, id))
                }>
                    <Icon icon='trash' style={{color: "red"}}/>
                </a>
            }
        </div>
    </div>
}

function artifactBlock(artifacts, fetchDocumentArtifact, selectedComponent, selectedMinor, selectedVersion, selectedDocument, adminMode, deleteArtifact, showConfirmation) {
    return artifacts.map(artifact => {
        const {fileName, id, displayName} = artifact
        return <ArtifactLabel
            key={id}
            displayName={displayName}
            id={id}
            fetchDocument={fetchDocumentArtifact}
            selectedComponent={selectedComponent}
            selectedMinor={selectedMinor}
            selectedVersion={selectedVersion}
            selectedDocument={selectedDocument}
            fileName={fileName}
            isPrintable={isPrintableArtifact(artifact)}
            isDeletable={adminMode}
            deleteArtifact={deleteArtifact}
            showConfirmation={showConfirmation}
        />
    })
}
